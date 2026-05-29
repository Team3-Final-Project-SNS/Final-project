// src/pages/PlaceVerificationPage.tsx (또는 기존 경로 유지)
import {useState, useEffect, useRef} from 'react';
import {useParams, useNavigate} from 'react-router';
import {MapPin, Check, Loader2} from 'lucide-react';
import {createPlaceVerification, updateMyLocation, getLocations, getMeetVerification} from '../../api/meetApi';
import {getMatchDetail} from '../../api/matchApi';

// ── 타입 정의 (기존과 동일) ──────────────────────────────
interface Position {
    latitude: number;
    longitude: number;
}

interface VerificationStatus {
    authorVerified: boolean;
    applicantVerified: boolean;
}

export default function PlaceVerificationPage() {
    const {id} = useParams();
    const navigate = useNavigate();

    // ── 기존 상태 (그대로 유지) ──────────────────────────
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

    // ── KakaoMap 관련 ref (리렌더링돼도 인스턴스 유지) ──
    // ref에 담는 이유: useState로 담으면 setMap 호출마다 리렌더링 → 지도 깜빡임 발생
    const mapContainerRef = useRef<HTMLDivElement>(null); // 지도 DOM 컨테이너
    const mapRef = useRef<kakao.maps.Map | null>(null);          // 카카오 Map 인스턴스
    const placeMarkerRef = useRef<kakao.maps.Marker | null>(null);      // 약속 장소 고정 마커
    const placeCircleRef = useRef<kakao.maps.Circle | null>(null);      // 50m 반경 원
    const myOverlayRef = useRef<kakao.maps.CustomOverlay | null>(null); // 내 위치 파란 점
    const opponentOverlayRef = useRef<kakao.maps.CustomOverlay | null>(null); // 상대 위치 점

    // ── 1. 매칭 정보 조회 (기존과 동일) ─────────────────
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

    // ── 2. KakaoMap 초기화 ───────────────────────────────
    // meetingPlace가 세팅된 후에만 지도를 그릴 수 있으므로 의존성 배열에 포함
    useEffect(() => {
        // meetingPlace 없으면 아직 API 응답 전 → 건너뜀
        if (!meetingPlace || !mapContainerRef.current) return;

        // window.kakao 없으면 SDK 로드 실패 → fallback 처리
        if (!window.kakao || !window.kakao.maps) {
            console.error('KakaoMap SDK 로드 실패');
            return;
        }

        // autoload=false이므로 kakao.maps.load() 콜백 안에서만 초기화해야 안전
        // 이유: DOM이 준비되기 전에 new kakao.maps.Map() 호출하면 에러
        // 이미 초기화된 경우 중복 실행 방지
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

        const placeMarker = new window.kakao.maps.Marker({
            map,
            position: center,
        });
        placeMarkerRef.current = placeMarker;

        const circle = new window.kakao.maps.Circle({
            map,
            center,
            radius: 50,
            strokeWeight: 2,
            strokeColor: '#d84315',
            strokeOpacity: 0.8,
            fillColor: '#ff7043',
            fillOpacity: 0.15,
        });
        placeCircleRef.current = circle;


        // 컴포넌트 언마운트 시 지도 인스턴스 참조 초기화
        // (SDK 자체가 DOM을 관리하므로 별도 destroy 불필요)
        return () => {
            mapRef.current = null;
            myOverlayRef.current = null;
            opponentOverlayRef.current = null;
        };
    }, [meetingPlace]); // meetingPlace가 세팅된 후 1회 실행

    // ── 3. GPS 위치 추적 (기존 로직 유지, 거리 계산만 교체) ─
    useEffect(() => {
        if (!meetingPlace) return;

        if (useSimulation) {
            // 테스트 모드: 80m → 0m 시뮬레이션
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

        // 실제 GPS 추적
        const watchId = navigator.geolocation.watchPosition(
            (position) => {
                const pos = {
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                };
                setCurrentPosition(pos);

                // KakaoMap SDK geometry로 거리 계산
                // SDK 로드 전이면 fallback으로 Haversine 직접 계산
                let dist: number;
                if (window.kakao?.maps?.geometry?.Sphere) {
                    const from = new window.kakao.maps.LatLng(pos.latitude, pos.longitude);
                    const to = new window.kakao.maps.LatLng(
                        meetingPlace.latitude,
                        meetingPlace.longitude
                    );
                    // computeDistanceBetween: 두 LatLng 간 직선거리(미터) 반환
                    dist = window.kakao.maps.geometry.Sphere.computeDistanceBetween(from, to);
                } else {
                    // SDK 미로드 시 기존 Haversine 공식으로 fallback
                    dist = calculateDistanceFallback(
                        pos.latitude, pos.longitude,
                        meetingPlace.latitude, meetingPlace.longitude
                    );
                }

                setDistance(dist);
                setIsWithinRange(dist <= 50);
                setLocationError(null);
            },
            (error) => {
                console.error('위치 추적 오류:', error);
                setLocationError('위치 정보를 가져올 수 없습니다. GPS를 켜주세요.');
            },
            {enableHighAccuracy: true}
        );

        return () => navigator.geolocation.clearWatch(watchId);
    }, [useSimulation, meetingPlace]);

    // ── 4. 내 위치 마커 업데이트 ─────────────────────────
    // currentPosition이 바뀔 때마다 지도 위 내 위치 오버레이를 갱신
    useEffect(() => {
        // 지도 미초기화면 건너뜀
        if (!mapRef.current || !currentPosition) return;

        const latlng = new window.kakao.maps.LatLng(
            currentPosition.latitude,
            currentPosition.longitude
        );

        if (myOverlayRef.current) {
            // 이미 오버레이가 있으면 위치만 이동 (새로 생성하면 깜빡임 발생)
            myOverlayRef.current.setPosition(latlng);
        } else {
            // 첫 위치 수신 시 오버레이 생성
            // content: HTML 문자열로 파란 원 마커 표현
            const myOverlay = new window.kakao.maps.CustomOverlay({
                map: mapRef.current,
                position: latlng,
                // 파란 원 + 흰 테두리 디자인
                content: `
                  <div style="
                    width: 16px; height: 16px;
                    background: #2196f3;
                    border: 3px solid white;
                    border-radius: 50%;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                  "></div>
                `,
                yAnchor: 0.5, // 원의 중심이 좌표에 정확히 위치
                xAnchor: 0.5,
            });
            myOverlayRef.current = myOverlay;
        }
    }, [currentPosition]);

    // ── 5. 상대방 위치 마커 업데이트 ─────────────────────
    useEffect(() => {
        if (!mapRef.current || !opponentPosition) return;

        const latlng = new window.kakao.maps.LatLng(
            opponentPosition.latitude,
            opponentPosition.longitude
        );

        if (opponentOverlayRef.current) {
            // 상대방도 기존 오버레이 위치만 갱신
            opponentOverlayRef.current.setPosition(latlng);
        } else {
            // 상대방 마커 (회색 원으로 구분)
            const opponentOverlay = new window.kakao.maps.CustomOverlay({
                map: mapRef.current,
                position: latlng,
                content: `
                  <div style="
                    width: 16px; height: 16px;
                    background: #9e9e9e;
                    border: 3px solid white;
                    border-radius: 50%;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                  "></div>
                `,
                yAnchor: 0.5,
                xAnchor: 0.5,
            });
            opponentOverlayRef.current = opponentOverlay;
        }
    }, [opponentPosition]);

    // ── 6. 위치 전송 + 상대방 위치/인증 상태 폴링 (기존과 동일) ─
    useEffect(() => {
        if (!id || !currentPosition) return;
        const matchId = Number(id);

        const intervalId = setInterval(async () => {
            // 내 위치 서버 전송 (5초마다)
            updateMyLocation(matchId, currentPosition.latitude, currentPosition.longitude)
                .catch(console.error);

            try {
                const [locRes, verRes] = await Promise.all([
                    getLocations(matchId),
                    getMeetVerification(matchId),
                ]);

                // 상대방 위치 갱신 → useEffect [opponentPosition]이 마커 업데이트
                const locData = locRes.data.data;
                if (locData.opponentLocation) {
                    setOpponentPosition({
                        latitude: locData.opponentLocation.latitude,
                        longitude: locData.opponentLocation.longitude,
                    });
                }

                // 인증 상태 갱신
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
        }, 1000); // API 명세서 기준 1초 폴링

        return () => clearInterval(intervalId);
    }, [id, currentPosition, navigate]);

    // ── SDK 미로드 시 fallback 거리 계산 (Haversine) ────
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

    // ── 장소 인증 (기존과 동일) ──────────────────────────
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

    // ── 로딩 화면 ─────────────────────────────────────────
    if (loading || !meetingPlace) {
        return (
            <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="animate-spin text-[#d84315] mb-4" size={40}/>
                <p className="text-[#616161]">정보를 불러오는 중...</p>
            </div>
        );
    }

    const bothVerified = verificationStatus.authorVerified && verificationStatus.applicantVerified;

    // ── 렌더링 ────────────────────────────────────────────
    return (
        <div className="max-w-2xl mx-auto p-4">
            <div className="bg-white rounded-2xl shadow-lg p-8">

                {/* 헤더 */}
                <div className="text-center mb-6">
                    <div className="flex items-center justify-center gap-2 mb-3">
                        <MapPin size={24} className="text-[#d84315]"/>
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

                {/*
                  ★ 핵심 변경 포인트 ★
                  기존: <svg> 원형 맵
                  변경: div ref에 KakaoMap SDK가 지도를 직접 그려줌
                  - 높이를 고정(h-64 = 256px)으로 줘야 지도가 정상 렌더링됨
                  - 높이 없으면 지도 영역이 0px → 빈 화면
                */}
                <div
                    ref={mapContainerRef}
                    className="w-full h-64 rounded-2xl overflow-hidden mb-6 border-2 border-[#e0e0e0]"
                    // KakaoMap은 이 div의 크기를 기준으로 지도를 렌더링
                />

                {/* 거리 바 (기존과 동일) */}
                {distance !== null && (
                    <div className="mb-6">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-[#616161]">현재 거리</span>
                            <span
                                className={`text-lg font-bold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
                                {distance.toFixed(1)}m / 60m
                            </span>
                        </div>
                        <div className="relative w-full h-3 bg-[#e0e0e0] rounded-full overflow-hidden">
                            <div
                                className={`h-full transition-all duration-300 ${isWithinRange ? 'bg-[#4caf50]' : 'bg-[#ef5350]'}`}
                                style={{width: `${Math.min((distance / 60) * 100, 100)}%`}}
                            />
                        </div>
                    </div>
                )}

                {/* 인증 현황 (기존과 동일) */}
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
                            <span
                                className={verificationStatus.applicantVerified ? 'text-[#4caf50]' : 'text-[#9e9e9e]'}>
                                {verificationStatus.applicantVerified ? '완료 ✅' : '대기'}
                            </span>
                        </div>
                    </div>
                </div>

                {/* 버튼 (기존과 동일) */}
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

                {/* 테스트 모드 (기존과 동일) */}
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

                    {useSimulation && (
                        <button
                            onClick={async () => {
                                try {
                                    // axiosInstance 직접 호출 대신, 이미 import된 createPlaceVerification 재사용.
                                    // id: useParams()에서 받은 matchId (이 파일에서 쓰는 변수명은 'id')
                                    // 좌표: match 7 → post 18의 실제 만남 장소 좌표를 그대로 전송
                                    //        → 백엔드가 같은 좌표로 거리를 재므로 거리 ≈ 0m → 60m 이내 → 통과
                                    const res = await createPlaceVerification(Number(id), {
                                        currentLat: 37.5979281,  // post 18 PLACE_LAT (인천검단26단지아파트)
                                        currentLng: 126.7159515, // post 18 PLACE_LNG
                                    });

                                    // 백엔드 응답의 verificationStatus로 인증 완료 여부 판단
                                    // (기존 handleVerify와 동일한 응답 구조를 그대로 활용)
                                    const status = res.data.data.verificationStatus;
                                    if (status === 'VERIFIED' || status === 'PENDING') {
                                        setIsVerified(true);
                                    }
                                    console.log("장소 인증 성공 — 백엔드 status 갱신됨:", status);

                                } catch (e: unknown) {
                                    // e가 unknown 타입이므로 axios 에러인지 타입 체크 후 메시지 추출
                                    // (기존 handleVerify의 패턴과 동일하게 맞춤)
                                    const err = e as { response?: { data?: { message?: string } } };
                                    console.error("장소 인증 실패:", err?.response?.data ?? e);
                                }
                            }}
                            className="mt-3 w-full bg-[#ef6c00] text-white py-2 rounded-lg text-sm font-semibold hover:bg-[#e65100] transition-all"
                        >
                            🧪 GPS 인증 강제 완료 (테스트용)
                        </button>
                    )}
                </div>

            </div>
        </div>
    );
}