import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';
import { MapPin, Navigation, Check, AlertCircle } from 'lucide-react';
import { createPlaceVerification, updateMyLocation, getLocations, getMatch } from '../../api/meetApi';

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

    // 추가
    const [meetingPlace, setMeetingPlace] = useState<{
        name: string;
        time: string;
        latitude: number;
        longitude: number;
    } | null>(null);

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

    // ✅ 추가: 상대방 위치 상태 (서버 폴링으로 받아옴, 처음엔 null)
    const [opponentPosition, setOpponentPosition] = useState<Position | null>(null);

    // 마운트 시 매칭 정보 1회 조회 → 약속 장소/시간 세팅
    useEffect(() => {
        if (!id) return;

        const fetchMatch = async () => {
            try {
                const res = await getMatch(Number(id));
                const data = res.data.data;
                // 서버에서 받은 실제 약속 장소/시간으로 세팅
                setMeetingPlace({
                    name: data.placeName,
                    // meetAt: "2026-05-16T13:30:00" → "13:30" 형태로 변환
                    time: new Date(data.meetAt).toLocaleTimeString('ko-KR', {
                        hour: '2-digit',
                        minute: '2-digit',
                    }),
                    latitude: data.placeLat,
                    longitude: data.placeLng,
                });
            } catch (err) {
                console.error('매칭 정보 조회 실패:', err);
                alert('매칭 정보를 불러오지 못했습니다.');
            }
        };

        fetchMatch();
    }, [id]);

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

    // Geolocation 오류 메시지 변환
    const getGeolocationErrorMessage = (error: GeolocationPositionError): string => {
        if (error.message.includes('permissions policy') || error.message.includes('disabled in this document')) {
            return '현재 환경에서는 GPS를 사용할 수 없습니다. 테스트 모드를 사용해주세요.';
        }

        switch (error.code) {
            case error.PERMISSION_DENIED:
                return '위치 권한이 거부되었습니다. 브라우저 설정에서 위치 권한을 허용해주세요.';
            case error.POSITION_UNAVAILABLE:
                return '위치 정보를 사용할 수 없습니다. GPS가 활성화되어 있는지 확인해주세요.';
            case error.TIMEOUT:
                return '위치 요청 시간이 초과되었습니다. 다시 시도해주세요.';
            default:
                return `위치 추적 중 오류가 발생했습니다: ${error.message}`;
        }
    };

    // 기존 GPS 위치 추적 useEffect (watchPosition) - 변경 없음
    useEffect(() => {
        if (!isTracking) return;

        if (useSimulation) {
            let simulatedDistance = 80;
            const interval = setInterval(() => {
                simulatedDistance = Math.max(0, simulatedDistance - 10);

                const angle = 0.5;
                const latOffset = (simulatedDistance / 111000) * Math.cos(angle);
                const lonOffset = (simulatedDistance / (111000 * Math.cos((meetingPlace?.latitude ?? 0) * Math.PI / 180))) * Math.sin(angle);

                const pos: Position = {
                    latitude: (meetingPlace?.latitude ?? 0) + latOffset,
                    longitude: (meetingPlace?.longitude ?? 0) + lonOffset,
                };

                setCurrentPosition(pos);
                setDistance(simulatedDistance);
                setIsWithinRange(simulatedDistance <= 50);
                setLocationError(null);

                if (simulatedDistance === 0) {
                    clearInterval(interval);
                }
            }, 1000);

            return () => clearInterval(interval);
        }

        if (!navigator.geolocation) {
            setLocationError('위치 서비스를 지원하지 않는 브라우저입니다.');
            return;
        }

        const watchId = navigator.geolocation.watchPosition(
            (position) => {
                const pos: Position = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                };
                setCurrentPosition(pos);
                setLocationError(null);

                const dist = calculateDistance(
                    pos.latitude,
                    pos.longitude,
                    meetingPlace?.latitude ?? 0,
                    meetingPlace?.longitude ?? 0
                );
                setDistance(dist);
                setIsWithinRange(dist <= 50);
            },
            (error) => {
                console.error('위치 추적 오류:', error);
                const errorMessage = getGeolocationErrorMessage(error);
                setLocationError(errorMessage);

                if (error.message.includes('permissions policy') || error.message.includes('disabled in this document')) {
                    setUseSimulation(true);
                }
            },
            {
                enableHighAccuracy: true,
                timeout: 10000,
                maximumAge: 0,
            }
        );

        return () => {
            navigator.geolocation.clearWatch(watchId);
        };
    }, [isTracking, useSimulation, meetingPlace?.latitude, meetingPlace?.longitude]);

    // ✅ 추가: 내 위치 서버 전송 + 상대방 위치 폴링 useEffect
    useEffect(() => {
        // matchId 없으면 실행 안 함
        if (!id) return;

        const matchId = Number(id);

        // 내 위치를 서버로 전송하는 함수
        const sendMyLocation = async () => {
            // currentPosition이 없으면 GPS 아직 못 받은 것 → 전송 skip
            if (!currentPosition) return;

            try {
                await updateMyLocation(
                    matchId,
                    parseFloat(currentPosition.latitude.toFixed(7)),  // 소수점 7자리로 맞춤
                    parseFloat(currentPosition.longitude.toFixed(7))
                );
            } catch (err) {
                // 1초마다 재시도되므로 alert 없이 콘솔만 출력
                console.error('위치 전송 실패:', err);
            }
        };

        // 상대방 위치를 서버에서 조회하는 함수
        const pollOpponentLocation = async () => {
            try {
                const res = await getLocations(matchId);
                const data = res.data.data;
                // 상대방 위치가 있을 때만 업데이트 (없으면 null 유지)
                if (data.opponentLocation) {
                    setOpponentPosition({
                        latitude: data.opponentLocation.latitude,
                        longitude: data.opponentLocation.longitude,
                    });
                }
            } catch (err) {
                console.error('상대방 위치 조회 실패:', err);
            }
        };

        // 마운트 시 즉시 1회 실행
        sendMyLocation();
        pollOpponentLocation();

        // 1초마다 반복
        const intervalId = setInterval(() => {
            sendMyLocation();
            pollOpponentLocation();
        }, 1000);

        // 언마운트 시 인터벌 정리 (메모리 누수 방지)
        return () => clearInterval(intervalId);

        // currentPosition이 바뀔 때마다 재실행
        // → GPS가 처음 잡히는 순간 바로 서버에 전송됨
    }, [id, currentPosition]);

    const handleVerify = async () => {
        if (!isWithinRange || !currentPosition) return;

        try {
            const res = await createPlaceVerification(
                Number(id),
                currentPosition.latitude,
                currentPosition.longitude
            );

            const data = res.data.data;

            setIsVerified(true);
            setVerificationStatus({
                authorVerified: data.authorPlaceVerifiedAt !== null,
                applicantVerified: data.applicantPlaceVerifiedAt !== null,
            });

        } catch (err: any) {
            alert(err.response?.data?.message || '장소 인증에 실패했습니다.');
        }
    };

    const handleGoToQR = () => {
        navigate(`/matches/${id}/qr?role=author`);
    };

    const getStatusText = () => {
        if (locationError) return { text: '위치 오류', color: 'text-[#ef5350]' };
        if (!currentPosition) return { text: '위치 확인 중...', color: 'text-[#9e9e9e]' };
        if (isWithinRange) return { text: '범위 이내 ✅', color: 'text-[#4caf50]' };
        return { text: '범위 밖 ❌', color: 'text-[#ef5350]' };
    };

    if (!meetingPlace) {
        return <div className="text-center py-12 text-[#9e9e9e]">불러오는 중...</div>;
    }

    const status = getStatusText();
    const bothVerified = verificationStatus.authorVerified && verificationStatus.applicantVerified;

    return (
        <div className="max-w-2xl mx-auto">
            <div className="bg-white rounded-2xl shadow-lg p-8">
                {/* 상단 상태 표시 */}
                <div className="text-center mb-6">
                    <div className="flex items-center justify-center gap-2 mb-3">
                        <MapPin size={24} className="text-[#d84315]" />
                        <h1 className="text-2xl font-bold text-[#212121]">장소 인증</h1>
                    </div>
                    <p className={`text-lg font-semibold ${status.color}`}>{status.text}</p>
                    <div className="mt-3 text-[#616161]">
                        <p className="font-semibold text-[#212121]">{meetingPlace.name}</p>
                        <p className="text-sm">약속 시간: {meetingPlace.time}</p>
                    </div>
                </div>

                {/* 시각적 거리 표시 (원형 맵) */}
                <div className="relative bg-gradient-to-br from-[#fafafa] to-white rounded-2xl p-8 mb-6 border-2 border-[#e0e0e0]">
                    <div className="relative w-full h-64 flex items-center justify-center">
                        <svg viewBox="0 0 300 300" className="w-full h-full">
                            {/* 외곽 점선 원 */}
                            <circle cx="150" cy="150" r="120" fill="none" stroke="#e0e0e0" strokeWidth="2" strokeDasharray="5,5" />

                            {/* 50m 인증 가능 영역 원 */}
                            <circle
                                cx="150"
                                cy="150"
                                r="120"
                                fill={isWithinRange ? 'rgba(76, 175, 80, 0.1)' : 'rgba(224, 224, 224, 0.1)'}
                                stroke={isWithinRange ? '#4caf50' : '#e0e0e0'}
                                strokeWidth="2"
                            />

                            {/* 약속 장소 중심점 (빨간 핀) */}
                            <circle cx="150" cy="150" r="8" fill="#d84315" />
                            <circle cx="150" cy="150" r="12" fill="none" stroke="#d84315" strokeWidth="2" />

                            {/* 내 위치 표시 (파란 점) */}
                            {currentPosition && distance !== null && (
                                <>
                                    <circle
                                        cx={150 + Math.min((distance / 50) * 100, 130) * Math.cos(0.5)}
                                        cy={150 - Math.min((distance / 50) * 100, 130) * Math.sin(0.5)}
                                        r={Math.max(10, (10 / 50) * 120)}
                                        fill={isWithinRange ? 'rgba(33, 150, 243, 0.3)' : 'rgba(239, 83, 80, 0.3)'}
                                        stroke={isWithinRange ? '#2196f3' : '#ef5350'}
                                        strokeWidth="2"
                                    />
                                    <circle
                                        cx={150 + Math.min((distance / 50) * 100, 130) * Math.cos(0.5)}
                                        cy={150 - Math.min((distance / 50) * 100, 130) * Math.sin(0.5)}
                                        r="6"
                                        fill={isWithinRange ? '#2196f3' : '#ef5350'}
                                    />
                                </>
                            )}

                            {/* ✅ 추가: 상대방 위치 표시 (빨간 점) */}
                            {opponentPosition && (() => {
                                // 상대방과 약속장소 사이 거리 계산
                                const opponentDist = calculateDistance(
                                    opponentPosition.latitude,
                                    opponentPosition.longitude,
                                    meetingPlace.latitude,
                                    meetingPlace.longitude
                                );
                                // 거리 비례로 SVG 좌표 변환 (내 위치와 겹치지 않게 각도 다르게)
                                const opponentX = 150 + Math.min((opponentDist / 50) * 100, 130) * Math.cos(2.0);
                                const opponentY = 150 - Math.min((opponentDist / 50) * 100, 130) * Math.sin(2.0);
                                const opponentWithinRange = opponentDist <= 50;

                                return (
                                    <>
                                        {/* 상대방 오차 범위 원 */}
                                        <circle
                                            cx={opponentX}
                                            cy={opponentY}
                                            r={Math.max(10, (10 / 50) * 120)}
                                            fill={opponentWithinRange ? 'rgba(239, 83, 80, 0.3)' : 'rgba(200, 200, 200, 0.3)'}
                                            stroke={opponentWithinRange ? '#ef5350' : '#9e9e9e'}
                                            strokeWidth="2"
                                        />
                                        {/* 상대방 중심점 */}
                                        <circle
                                            cx={opponentX}
                                            cy={opponentY}
                                            r="6"
                                            fill={opponentWithinRange ? '#ef5350' : '#9e9e9e'}
                                        />
                                    </>
                                );
                            })()}

                            {/* 50m 표시 텍스트 */}
                            <text x="270" y="155" fontSize="12" fill="#9e9e9e" textAnchor="middle">
                                50m
                            </text>
                        </svg>
                    </div>

                    {/* 범례 */}
                    <div className="flex items-center justify-center gap-6 mt-4 text-sm">
                        <div className="flex items-center gap-2">
                            <div className="w-4 h-4 bg-[#d84315] rounded-full"></div>
                            <span className="text-[#616161]">약속 장소</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className={`w-4 h-4 rounded-full ${isWithinRange ? 'bg-[#2196f3]' : 'bg-[#ef5350]'}`}></div>
                            <span className="text-[#616161]">내 위치 (±10m)</span>
                        </div>
                        {/* ✅ 추가: 상대방 위치 범례 - 상대방 위치 수신됐을 때만 표시 */}
                        {opponentPosition && (
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded-full bg-[#ef5350]"></div>
                                <span className="text-[#616161]">상대방 위치</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* 거리 표시 */}
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

                {/* 오류 메시지 */}
                {locationError && !useSimulation && (
                    <div className="bg-[#ffebee] border border-[#ef5350] rounded-xl px-4 py-3 mb-6 flex items-start gap-2">
                        <AlertCircle size={20} className="text-[#ef5350] flex-shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <span className="text-[#c62828] text-sm">{locationError}</span>
                            {locationError.includes('테스트 모드') && (
                                <button
                                    onClick={() => setUseSimulation(true)}
                                    className="mt-2 w-full px-4 py-2 bg-[#2196f3] text-white rounded-lg text-sm font-semibold hover:bg-[#1976d2] transition-colors"
                                >
                                    테스트 모드로 전환하기
                                </button>
                            )}
                        </div>
                    </div>
                )}

                {/* 테스트 모드 안내 */}
                {useSimulation && (
                    <div className="bg-[#e3f2fd] border border-[#2196f3] rounded-xl px-4 py-3 mb-6 flex items-start gap-2">
                        <AlertCircle size={20} className="text-[#2196f3] flex-shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <p className="text-[#1565c0] text-sm font-semibold mb-1">🧪 테스트 모드 실행 중</p>
                            <p className="text-[#1976d2] text-xs">
                                GPS 대신 시뮬레이션으로 장소 인증을 테스트하고 있습니다.
                            </p>
                        </div>
                    </div>
                )}

                {/* 인증 현황 */}
                <div className="bg-gradient-to-br from-[#f5f5f5] to-white rounded-xl p-5 mb-6 border border-[#e0e0e0]">
                    <h3 className="font-semibold text-[#212121] mb-4 flex items-center gap-2">
                        <span className="w-2 h-2 bg-[#4caf50] rounded-full"></span>
                        인증 현황
                    </h3>
                    <div className="space-y-3">
                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <div className="flex items-center gap-3">
                                <div className={`w-8 h-8 rounded-full flex items-center justify-center ${verificationStatus.authorVerified ? 'bg-[#4caf50]' : 'bg-[#e0e0e0]'}`}>
                                    {verificationStatus.authorVerified ? <Check size={16} className="text-white" /> : <span className="text-sm">⏱</span>}
                                </div>
                                <span className="text-sm font-medium text-[#212121]">나 (등록자)</span>
                            </div>
                            <div className="text-right">
                                <span className={`text-xs font-semibold block ${verificationStatus.authorVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}`}>
                                    {verificationStatus.authorVerified ? '완료' : '대기'}
                                </span>
                                <span className="text-xs text-[#9e9e9e]">
                                    {verificationStatus.authorVerified ? '장소 인증됨' : '인증 대기 중'}
                                </span>
                            </div>
                        </div>

                        <div className="flex items-center justify-between bg-white rounded-lg p-3">
                            <div className="flex items-center gap-3">
                                <div className={`w-8 h-8 rounded-full flex items-center justify-center ${verificationStatus.applicantVerified ? 'bg-[#4caf50]' : 'bg-[#e0e0e0]'}`}>
                                    {verificationStatus.applicantVerified ? <Check size={16} className="text-white" /> : <span className="text-sm">⏱</span>}
                                </div>
                                <span className="text-sm font-medium text-[#212121]">밥먹자 (신청자)</span>
                            </div>
                            <div className="text-right">
                                <span className={`text-xs font-semibold block ${verificationStatus.applicantVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}`}>
                                    {verificationStatus.applicantVerified ? '완료' : '대기'}
                                </span>
                                <span className="text-xs text-[#9e9e9e]">
                                    {verificationStatus.applicantVerified ? '장소 인증됨' : '인증 대기 중'}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* 양측 인증 완료 시 QR 단계로 */}
                {bothVerified ? (
                    <div className="space-y-4">
                        <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-xl px-4 py-4 text-center">
                            <p className="text-[#2e7d32] font-semibold mb-2">✅ 양측 장소 인증 완료!</p>
                            <p className="text-[#2e7d32] text-sm">이제 QR 인증으로 만남을 확정하세요</p>
                        </div>
                        <button
                            onClick={handleGoToQR}
                            className="w-full bg-[#4caf50] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#43a047] transition-all shadow-md hover:shadow-lg flex items-center justify-center gap-2"
                        >
                            <Navigation size={20} />
                            QR 인증 단계로 이동
                        </button>
                    </div>
                ) : (
                    <div>
                        {!isVerified ? (
                            <>
                                <button
                                    onClick={handleVerify}
                                    disabled={!isWithinRange || locationError !== null}
                                    className={`w-full py-4 rounded-xl font-bold text-lg transition-all shadow-md ${
                                        isWithinRange && !locationError
                                            ? 'bg-[#d84315] text-white hover:bg-[#bf360c] hover:shadow-lg'
                                            : 'bg-[#e0e0e0] text-[#9e9e9e] cursor-not-allowed'
                                    }`}
                                >
                                    {isWithinRange && !locationError ? '장소 인증하기' : '약속 장소로 이동해주세요'}
                                </button>
                                <p className="text-center text-xs text-[#9e9e9e] mt-3">
                                    50m 이내 진입 시 인증 버튼이 활성화됩니다
                                </p>
                            </>
                        ) : (
                            <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-xl px-4 py-4 text-center">
                                <Check size={32} className="text-[#4caf50] mx-auto mb-2" />
                                <p className="text-[#2e7d32] font-semibold">장소 인증 완료!</p>
                                <p className="text-[#2e7d32] text-sm mt-1">상대방 인증을 기다리는 중...</p>
                            </div>
                        )}
                    </div>
                )}

                {/* 도움말 */}
                <div className="mt-6 bg-[#fff3e0] border border-[#ff9800] rounded-xl px-4 py-3">
                    <p className="text-[#ef6c00] text-sm mb-2">
                        💡 <strong>Tip:</strong> GPS 위치 정확도를 높이려면 실외에서 사용해주세요.
                    </p>
                    <div className="mt-3 pt-3 border-t border-[#ffe0b2]">
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={useSimulation}
                                onChange={(e) => setUseSimulation(e.target.checked)}
                                className="w-4 h-4"
                            />
                            <span className="text-[#ef6c00] text-xs">
                                테스트 모드 (GPS 없이 시뮬레이션)
                            </span>
                        </label>
                    </div>
                </div>

                {/* 위치 권한 안내 */}
                {locationError && locationError.includes('권한') && !useSimulation && (
                    <div className="mt-4 bg-[#e3f2fd] border border-[#2196f3] rounded-xl px-4 py-3">
                        <p className="text-[#1565c0] text-sm font-semibold mb-2">📱 위치 권한 허용 방법</p>
                        <ul className="text-[#1976d2] text-xs space-y-1 ml-4 list-disc">
                            <li>Chrome: 주소창 왼쪽 자물쇠 아이콘 → 위치 → 허용</li>
                            <li>Safari: 설정 → Safari → 위치 → 허용</li>
                            <li>Firefox: 주소창 왼쪽 자물쇠 → 권한 → 위치 → 허용</li>
                        </ul>
                    </div>
                )}
            </div>
        </div>
    );
}