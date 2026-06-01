import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router';
import { MapPin, Loader2, Navigation } from 'lucide-react';
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
    const [useSimulation, setUseSimulation] = useState(false);
    const [opponentPosition, setOpponentPosition] = useState<Position | null>(null);

    // ★ 추가: 카카오맵 사용 가능 여부 상태
    // false가 되면 SVG fallback으로 전환
    const [kakaoMapAvailable, setKakaoMapAvailable] = useState(true);

    // KakaoMap ref
    const mapContainerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<kakao.maps.Map | null>(null);
    const myOverlayRef = useRef<kakao.maps.CustomOverlay | null>(null);
    const opponentOverlayRef = useRef<kakao.maps.CustomOverlay | null>(null);

    // 1. 매칭 정보 조회
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

    // 2. KakaoMap 초기화 — try/catch로 장애 감지
    useEffect(() => {
        if (!meetingPlace || !mapContainerRef.current) return;

        // ★ SDK 로드 실패 감지 — window.kakao 자체가 없으면 SVG fallback
        if (!window.kakao?.maps) {
            console.warn('KakaoMap SDK 로드 실패 → SVG fallback으로 전환');
            setKakaoMapAvailable(false);
            return;
        }

        try {
            // ★ 지도 초기화 중 에러 감지 — try/catch로 SVG fallback
            if (mapRef.current) return;

            const center = new window.kakao.maps.LatLng(
                meetingPlace.latitude,
                meetingPlace.longitude
            );

            const map = new window.kakao.maps.Map(mapContainerRef.current!, {
                center,
                level: 3,
            });
            mapRef.current = map;

            new window.kakao.maps.Marker({ map, position: center });

            new window.kakao.maps.Circle({
                map,
                center,
                radius: 50,
                strokeWeight: 2,
                strokeColor: '#d84315',
                strokeOpacity: 0.8,
                fillColor: '#ff7043',
                fillOpacity: 0.15,
            });

        } catch (e) {
            // ★ 초기화 중 예외 발생 → SVG fallback으로 전환
            console.error('KakaoMap 초기화 실패 → SVG fallback으로 전환:', e);
            setKakaoMapAvailable(false);
        }

        return () => {
            mapRef.current = null;
            myOverlayRef.current = null;
            opponentOverlayRef.current = null;
        };
    }, [meetingPlace]);

    // 3. GPS 위치 추적
    useEffect(() => {
        if (!meetingPlace) return;

        if (useSimulation) {
            let simulatedDistance = 80;
            const interval = setInterval(() => {
                simulatedDistance = Math.max(0, simulatedDistance - 10);
                const pos: Position = {
                    latitude: meetingPlace.latitude + simulatedDistance / 111000,
                    longitude: meetingPlace.longitude,
                };
                setCurrentPosition(pos);
                setDistance(simulatedDistance);
                setIsWithinRange(simulatedDistance <= 60);
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

                let dist: number;
                if (window.kakao?.maps?.geometry?.Sphere) {
                    const from = new window.kakao.maps.LatLng(pos.latitude, pos.longitude);
                    const to = new window.kakao.maps.LatLng(
                        meetingPlace.latitude,
                        meetingPlace.longitude
                    );
                    dist = window.kakao.maps.geometry.Sphere.computeDistanceBetween(from, to);
                } else {
                    // ★ geometry 없어도 Haversine으로 fallback → 장소 인증 정상 작동
                    dist = calculateDistanceFallback(
                        pos.latitude, pos.longitude,
                        meetingPlace.latitude, meetingPlace.longitude
                    );
                }

                setDistance(dist);
                setIsWithinRange(dist <= 60);
                setLocationError(null);
            },
            (error) => {
                console.error('위치 추적 오류:', error);
                setLocationError('위치 정보를 가져올 수 없습니다. GPS를 켜주세요.');
            },
            { enableHighAccuracy: true }
        );

        return () => navigator.geolocation.clearWatch(watchId);
    }, [useSimulation, meetingPlace]);

    // 4. 내 위치 마커 업데이트 (카카오맵 정상일 때만)
    useEffect(() => {
        if (!kakaoMapAvailable || !mapRef.current || !currentPosition) return;

        const latlng = new window.kakao.maps.LatLng(
            currentPosition.latitude,
            currentPosition.longitude
        );

        if (myOverlayRef.current) {
            myOverlayRef.current.setPosition(latlng);
        } else {
            myOverlayRef.current = new window.kakao.maps.CustomOverlay({
                map: mapRef.current,
                position: latlng,
                content: `<div style="
                    width: 16px; height: 16px;
                    background: #2196f3;
                    border: 3px solid white;
                    border-radius: 50%;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                "></div>`,
                yAnchor: 0.5,
                xAnchor: 0.5,
            });
        }
    }, [currentPosition, kakaoMapAvailable]);

    // 5. 상대방 위치 마커 업데이트 (카카오맵 정상일 때만)
    useEffect(() => {
        if (!kakaoMapAvailable || !mapRef.current || !opponentPosition) return;

        const latlng = new window.kakao.maps.LatLng(
            opponentPosition.latitude,
            opponentPosition.longitude
        );

        if (opponentOverlayRef.current) {
            opponentOverlayRef.current.setPosition(latlng);
        } else {
            opponentOverlayRef.current = new window.kakao.maps.CustomOverlay({
                map: mapRef.current,
                position: latlng,
                content: `<div style="
                    width: 16px; height: 16px;
                    background: #9e9e9e;
                    border: 3px solid white;
                    border-radius: 50%;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                "></div>`,
                yAnchor: 0.5,
                xAnchor: 0.5,
            });
        }
    }, [opponentPosition, kakaoMapAvailable]);

    // 6. 위치 전송 + 폴링
    useEffect(() => {
        if (!id || !currentPosition) return;
        const matchId = Number(id);

        const intervalId = setInterval(async () => {
            updateMyLocation(matchId, currentPosition.latitude, currentPosition.longitude)
                .catch(console.error);

            try {
                const [locRes, verRes] = await Promise.all([
                    getLocations(matchId),
                    getMeetVerification(matchId),
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

    // Haversine fallback 거리 계산
    const calculateDistanceFallback = (
        lat1: number, lon1: number,
        lat2: number, lon2: number
    ): number => {
        const R = 6371e3;
        const φ1 = (lat1 * Math.PI) / 180;
        const φ2 = (lat2 * Math.PI) / 180;
        const Δφ = ((lat2 - lat1) * Math.PI) / 180;
        const Δλ = ((lon2 - lon1) * Math.PI) / 180;
        const a =
            Math.sin(Δφ / 2) ** 2 +
            Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    };

    // 장소 인증
    const handleVerify = async () => {
        if (!isWithinRange || !currentPosition) return;
        try {
            const res = await createPlaceVerification(Number(id), {
                currentLat: currentPosition.latitude,
                currentLng: currentPosition.longitude,
            });
            if (
                res.data.data.verificationStatus === 'VERIFIED' ||
                res.data.data.verificationStatus === 'PENDING'
            ) {
                setIsVerified(true);
            }
        } catch (err: any) {
            alert(err.response?.data?.message || '장소 인증에 실패했습니다.');
        }
    };

    const handleGoToQR = () => navigate(`/matches/${id}/qr`);

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

                {/* 헤더 */}
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
                    {locationError && (
                        <p className="text-sm text-[#ef5350] mt-2">{locationError}</p>
                    )}
                </div>

                {/* ★ 핵심: 카카오맵 정상 / 장애 분기 */}
                {kakaoMapAvailable ? (
                    // 카카오맵 정상 — 실제 지도 렌더링
                    <div
                        ref={mapContainerRef}
                        className="w-full h-64 rounded-2xl overflow-hidden mb-6 border-2 border-[#e0e0e0]"
                    />
                ) : (
                    // ★ 카카오맵 장애 — SVG fallback
                    <div className="relative bg-[#fafafa] rounded-2xl p-8 mb-6 border-2 border-[#e0e0e0]">
                        {/* 장애 안내 배너 */}
                        <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg px-3 py-2 mb-4 text-center">
                            <p className="text-xs text-[#e65100]">
                                ⚠️ 지도를 불러올 수 없습니다. 거리 기반 인증은 정상 작동합니다.
                            </p>
                        </div>
                        {/* 기존 SVG 원형 지도 */}
                        <div className="relative w-full h-48 flex items-center justify-center">
                            <svg viewBox="0 0 300 300" className="w-full h-full">
                                {/* 50m 반경 원 */}
                                <circle
                                    cx="150" cy="150" r="120"
                                    fill="none"
                                    stroke="#e0e0e0"
                                    strokeWidth="2"
                                    strokeDasharray="5,5"
                                />
                                {/* 약속 장소 */}
                                <circle cx="150" cy="150" r="8" fill="#d84315" />
                                {/* 내 위치 (GPS 수신 후 표시) */}
                                {currentPosition && (
                                    <circle cx="150" cy="150" r="5" fill="#2196f3" />
                                )}
                            </svg>
                            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                                <Navigation size={40} className="text-[#d84315] mb-2 animate-pulse" />
                                <p className="text-xs text-[#9e9e9e]">실시간 위치 추적 중</p>
                            </div>
                        </div>
                    </div>
                )}

                {/* 거리 바 */}
                {distance !== null && (
                    <div className="mb-6">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-[#616161]">현재 거리</span>
                            <span className={`text-lg font-bold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
                                {distance.toFixed(1)}m / 60m
                            </span>
                        </div>
                        <div className="relative w-full h-3 bg-[#e0e0e0] rounded-full overflow-hidden">
                            <div
                                className={`h-full transition-all duration-300 ${isWithinRange ? 'bg-[#4caf50]' : 'bg-[#ef5350]'}`}
                                style={{ width: `${Math.min((distance / 60) * 100, 100)}%` }}
                            />
                        </div>
                    </div>
                )}

                {/* 인증 현황 */}
                <div className="bg-gradient-to-br from-[#f5f5f5] to-white rounded-xl p-5 mb-6 border border-[#e0e0e0]">
                    <h3 className="font-semibold text-[#212121] mb-4">인증 현황</h3>
                    <div className="space-y-3">
                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <span className="text-sm font-medium text-[#212121]">등록자</span>
                            <span className={verificationStatus.authorVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}>
                                {verificationStatus.authorVerified ? '완료 ✅' : '대기'}
                            </span>
                        </div>
                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <span className="text-sm font-medium text-[#212121]">신청자</span>
                            <span className={verificationStatus.applicantVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}>
                                {verificationStatus.applicantVerified ? '완료 ✅' : '대기'}
                            </span>
                        </div>
                    </div>
                </div>

                {/* 버튼 */}
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
                                <p className="text-[#2e7d32] font-semibold">
                                    장소 인증 완료! 상대방을 기다려주세요.
                                </p>
                            </div>
                        )}
                    </div>
                )}

                {/* 테스트 모드 */}
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