import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';
import { MapPin, Navigation, Check, AlertCircle, Loader2 } from 'lucide-react';
import { createPlaceVerification, updateMyLocation, getLocations, getMeetVerification } from '../../api/meetApi';
import { getMatchDetail } from '../../api/matchApi';

interface Position {
    latitude: number;
    longitude: number;
}

interface VerificationStatus {
    authorVerified: boolean;
    applicantVerified: boolean;
}

export default function PlaceVerificationPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    // 상태 관리
    const [meetingPlace, setMeetingPlace] = useState<{
        name: string;
        time: string;
        latitude: number;
        longitude: number;
    } | null>(null);

    const [loading, setLoading] = useState(true);
    const [currentPosition, setCurrentPosition] = useState<Position | null>(null);
    const [distance, setDistance] = useState<number | null>(null);
    const [isWithinRange, setIsWithinRange] = useState(false);
    const [isVerified, setIsVerified] = useState(false);
    const [verificationStatus, setVerificationStatus] = useState<VerificationStatus>({
        authorVerified: false,
        applicantVerified: false,
    });
    const [locationError, setLocationError] = useState<string | null>(null);
    const [isTracking, setIsTracking] = useState(true);
    const [useSimulation, setUseSimulation] = useState(false);

    // 상대방 위치 상태
    const [opponentPosition, setOpponentPosition] = useState<Position | null>(null);

    // 두 좌표 간 거리 계산 (Haversine formula)
    const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
        const R = 6371e3;
        const φ1 = (lat1 * Math.PI) / 180;
        const φ2 = (lat2 * Math.PI) / 180;
        const Δφ = ((lat2 - lat1) * Math.PI) / 180;
        const Δλ = ((lon2 - lon1) * Math.PI) / 180;

        const a =
            Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
            Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    };

    // 1. 마운트 시 매칭 정보 1회 조회
    useEffect(() => {
        if (!id) return;

        const fetchMatch = async () => {
            try {
                const res = await getMatchDetail(Number(id));
                const data = res.data.data;
                setMeetingPlace({
                    name: data.placeName,
                    time: new Date(data.meetAt).toLocaleTimeString('ko-KR', {
                        hour: '2-digit',
                        minute: '2-digit',
                    }),
                    latitude: data.placeLat,
                    longitude: data.placeLng,
                });
            } catch (err) {
                console.error('매칭 정보 조회 실패:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchMatch();
    }, [id]);

    // 2. GPS 위치 추적 (watchPosition)
    useEffect(() => {
        if (!isTracking || !meetingPlace) return;

        if (useSimulation) {
            let simulatedDistance = 80;
            const interval = setInterval(() => {
                simulatedDistance = Math.max(0, simulatedDistance - 10);
                const pos: Position = {
                    latitude: meetingPlace.latitude + (simulatedDistance / 111000),
                    longitude: meetingPlace.longitude,
                };
                setCurrentPosition(pos);
                setDistance(simulatedDistance);
                setIsWithinRange(simulatedDistance <= 50);
                if (simulatedDistance === 0) clearInterval(interval);
            }, 1000);
            return () => clearInterval(interval);
        }

        const watchId = navigator.geolocation.watchPosition(
            (position) => {
                const pos = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                };
                setCurrentPosition(pos);
                const dist = calculateDistance(pos.latitude, pos.longitude, meetingPlace.latitude, meetingPlace.longitude);
                setDistance(dist);
                setIsWithinRange(dist <= 50);
                setLocationError(null);
            },
            (error) => {
                console.error('위치 추적 오류:', error);
                setLocationError('위치 정보를 가져올 수 없습니다. GPS를 켜주세요.');
            },
            { enableHighAccuracy: true }
        );

        return () => navigator.geolocation.clearWatch(watchId);
    }, [isTracking, useSimulation, meetingPlace]);

    // 3. 내 위치 전송 + 상대방 위치/인증 상태 폴링 (1초 주기)
    useEffect(() => {
        if (!id || !currentPosition) return;
        const matchId = Number(id);

        const intervalId = setInterval(async () => {
            // 내 위치 전송
            updateMyLocation(matchId, currentPosition.latitude, currentPosition.longitude).catch(console.error);

            // 상대방 정보 폴링
            try {
                const [locRes, verRes] = await Promise.all([
                    getLocations(matchId),
                    getMeetVerification(matchId)
                ]);
                
                const locData = locRes.data.data;
                if (locData.opponentLocation) {
                    setOpponentPosition({
                        latitude: locData.opponentLocation.latitude,
                        longitude: locData.opponentLocation.longitude,
                    });
                }

                const verData = verRes.data.data;
                setVerificationStatus({
                    authorVerified: verData.authorPlaceVerifiedAt !== null,
                    applicantVerified: verData.applicantPlaceVerifiedAt !== null,
                });
                
                if (verData.verificationStatus === 'DONE') {
                    navigate('/matches');
                } else if (verData.verificationStatus === 'VERIFIED') {
                    setIsVerified(true);
                }
            } catch (err) {
                console.error('상태 조회 실패:', err);
            }
        }, 1000);

        return () => clearInterval(intervalId);
    }, [id, currentPosition, navigate]);

    // 장소 인증 수행
    const handleVerify = async () => {
        if (!isWithinRange || !currentPosition) return;

        try {
            const res = await createPlaceVerification(Number(id), {
                currentLat: currentPosition.latitude,
                currentLng: currentPosition.longitude
            });

            if (res.data.data.verificationStatus === 'VERIFIED' || res.data.data.verificationStatus === 'PENDING') {
                setIsVerified(true);
            }
        } catch (err: any) {
            alert(err.response?.data?.message || '장소 인증에 실패했습니다.');
        }
    };

    const handleGoToQR = () => {
        navigate(`/matches/${id}/qr`);
    };

    if (loading || !meetingPlace) {
        return (
            <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="animate-spin text-[#d84315] mb-4" size={40} />
                <p className="text-[#616161]">정보를 불러오는 중...</p>
            </div>
        );
    }

    const bothVerified = verificationStatus.authorVerified && verificationStatus.applicantVerified;

    return (
        <div className="max-w-2xl mx-auto p-4">
            <div className="bg-white rounded-2xl shadow-lg p-8">
                <div className="text-center mb-6">
                    <div className="flex items-center justify-center gap-2 mb-3">
                        <MapPin size={24} className="text-[#d84315]" />
                        <h1 className="text-2xl font-bold text-[#212121]">장소 인증</h1>
                    </div>
                    <p className={`text-lg font-semibold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
                        {isWithinRange ? '범위 이내 ✅' : '범위 밖 ❌'}
                    </p>
                    <div className="mt-3 text-[#616161]">
                        <p className="font-semibold text-[#212121]">{meetingPlace.name}</p>
                        <p className="text-sm">약속 시간: {meetingPlace.time}</p>
                    </div>
                </div>

                <div className="relative bg-[#fafafa] rounded-2xl p-8 mb-6 border-2 border-[#e0e0e0]">
                    <div className="relative w-full h-64 flex items-center justify-center">
                        <svg viewBox="0 0 300 300" className="w-full h-full">
                            <circle cx="150" cy="150" r="120" fill="none" stroke="#e0e0e0" strokeWidth="2" strokeDasharray="5,5" />
                            <circle cx="150" cy="150" r="8" fill="#d84315" />
                            {currentPosition && distance !== null && (
                                <circle cx={150} cy={150} r="5" fill="#2196f3" />
                            )}
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                            <Navigation size={48} className="text-[#d84315] mb-2 animate-pulse" />
                            <p className="text-xs text-[#9e9e9e]">실시간 위치 추적 중</p>
                        </div>
                    </div>
                </div>

                {distance !== null && (
                    <div className="mb-6">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-[#616161]">현재 거리</span>
                            <span className={`text-lg font-bold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
                                {distance.toFixed(1)}m / 50m
                            </span>
                        </div>
                        <div className="relative w-full h-3 bg-[#e0e0e0] rounded-full overflow-hidden">
                            <div
                                className={`h-full transition-all duration-300 ${isWithinRange ? 'bg-[#4caf50]' : 'bg-[#ef5350]'}`}
                                style={{ width: `${Math.min((distance / 50) * 100, 100)}%` }}
                            ></div>
                        </div>
                    </div>
                )}

                <div className="bg-gradient-to-br from-[#f5f5f5] to-white rounded-xl p-5 mb-6 border border-[#e0e0e0]">
                    <h3 className="font-semibold text-[#212121] mb-4 flex items-center gap-2">
                        인증 현황
                    </h3>
                    <div className="space-y-3">
                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <span className="text-sm font-medium text-[#212121]">등록자</span>
                            <span className={verificationStatus.authorVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}>
                                {verificationStatus.authorVerified ? '완료' : '대기'}
                            </span>
                        </div>
                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <span className="text-sm font-medium text-[#212121]">신청자</span>
                            <span className={verificationStatus.applicantVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}>
                                {verificationStatus.applicantVerified ? '완료' : '대기'}
                            </span>
                        </div>
                    </div>
                </div>

                {bothVerified ? (
                    <button
                        onClick={handleGoToQR}
                        className="w-full bg-[#4caf50] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#43a047] transition-all shadow-md"
                    >
                        QR 인증 단계로 이동
                    </button>
                ) : (
                    <div>
                        {!isVerified ? (
                            <button
                                onClick={handleVerify}
                                disabled={!isWithinRange}
                                className={`w-full py-4 rounded-xl font-bold text-lg transition-all shadow-md ${
                                    isWithinRange
                                        ? 'bg-[#d84315] text-white hover:bg-[#bf360c]'
                                        : 'bg-[#e0e0e0] text-[#9e9e9e] cursor-not-allowed'
                                }`}
                            >
                                장소 인증하기
                            </button>
                        ) : (
                            <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-xl px-4 py-4 text-center">
                                <p className="text-[#2e7d32] font-semibold">장소 인증 완료! 상대방을 기다려주세요.</p>
                            </div>
                        )}
                    </div>
                )}

                <div className="mt-6 pt-4 border-t border-[#e0e0e0]">
                    <label className="flex items-center gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={useSimulation}
                            onChange={(e) => setUseSimulation(e.target.checked)}
                            className="w-4 h-4"
                        />
                        <span className="text-[#ef6c00] text-xs">테스트 모드 (시뮬레이션)</span>
                    </label>
                </div>
            </div>
        </div>
    );
}
