import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ArrowLeft, Loader2, MapPin, Navigation } from 'lucide-react';
import {
  createPlaceVerification,
  getLocations,
  getMeetVerification,
  ParticipantVerification,
  updateMyLocation,
} from '@/api/meetApi';
import { getMatchDetail } from '@/api/matchApi';
import { getUserMe } from '@/api/userApi';
import { loadKakaoMaps } from '../utils/kakaoMapsLoader';

declare global {
  interface Window {
    kakao?: any;
  }
}

interface Position {
  latitude: number;
  longitude: number;
}

type KakaoMapStatus = 'loading' | 'ready' | 'error';

// GPS 장소 인증 정책 반경 50m와 위치 오차 허용 범위 10m를 합한 판정 반경
const USER_VISIBLE_RADIUS_METERS = 60;
// 발표회 시연을 위해 QR 단계 fallback 진입 기준 임시 조정
const QR_FALLBACK_AFTER_MINUTES = 10;
const LOCATION_INITIAL_TIMEOUT_MS = 8000;
const LOCATION_WATCH_TIMEOUT_MS = 20000;
const LOCATION_INITIAL_MAXIMUM_AGE_MS = 30000;
const LOCATION_WATCH_MAXIMUM_AGE_MS = 10000;

export default function PlaceVerificationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const matchId = Number(id);

  const blockCancelledParticipant = () => {
    alert('매칭 취소자는 인증 페이지에 접근할 수 없습니다.');
    navigate('/matches', { replace: true });
  };

  const [meetingPlace, setMeetingPlace] = useState<{
    name: string;
    time: string;
    meetAt: string;
    latitude: number;
    longitude: number;
  } | null>(null);
  const [chatRoomId, setChatRoomId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentPosition, setCurrentPosition] = useState<Position | null>(null);
  const [distance, setDistance] = useState<number | null>(null);
  const [isWithinRange, setIsWithinRange] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [isLocating, setIsLocating] = useState(false);
  const [locationRetryKey, setLocationRetryKey] = useState(0);
  const [kakaoMapStatus, setKakaoMapStatus] = useState<KakaoMapStatus>('loading');
  const [mapRetryKey, setMapRetryKey] = useState(0);
  const [authorNickname, setAuthorNickname] = useState('등록자');
  const [authorVerified, setAuthorVerified] = useState(false);
  const [verificationParticipants, setVerificationParticipants] = useState<ParticipantVerification[]>([]);
  const [isCurrentUserAuthor, setIsCurrentUserAuthor] = useState<boolean | null>(null);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const radiusCircleRef = useRef<any>(null);
  const myMarkerRef = useRef<any>(null);
  const opponentMarkerRefs = useRef<any[]>([]);

  const kakaoMapAvailable = kakaoMapStatus === 'ready';

  const applyVerificationData = (
    data: Awaited<ReturnType<typeof getMeetVerification>>['data']['data'],
    currentUserIsAuthor = isCurrentUserAuthor,
  ) => {
    setAuthorNickname(data.authorNickname || '등록자');
    setAuthorVerified(data.authorPlaceVerifiedAt !== null);
    setVerificationParticipants(data.participants || []);

    const myParticipant = data.participants?.find((participant) => participant.matchId === matchId);
    if (currentUserIsAuthor !== null) {
      const nextIsVerified = currentUserIsAuthor
        ? data.authorPlaceVerifiedAt !== null
        : myParticipant?.verified === true;
      setIsVerified(nextIsVerified);
    }

    if (data.verificationStatus === 'DONE') {
      navigate('/matches');
    }
  };

  useEffect(() => {
    if (!matchId) return;

    const fetchInitialData = async () => {
      setLoading(true);
      try {
        const [matchRes, verificationRes, userRes] = await Promise.all([
          getMatchDetail(matchId),
          getMeetVerification(matchId),
          getUserMe(),
        ]);
        const match = matchRes.data.data;
        const currentUserIsAuthor = userRes.data.data.userId === match.authorId;
        setIsCurrentUserAuthor(currentUserIsAuthor);
        setMeetingPlace({
          name: match.placeName,
          time: new Date(match.meetAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }),
          meetAt: match.meetAt,
          latitude: match.placeLat,
          longitude: match.placeLng,
        });
        setChatRoomId(match.chatRoomId);
        applyVerificationData(verificationRes.data.data, currentUserIsAuthor);
      } catch (err: any) {
        console.error('Failed to load place verification data', err);
        const code = err.response?.data?.code;
        if (code === 'MATCH_002' || code === 'CHAT_002' || code === 'CHAT_004') {
          blockCancelledParticipant();
        }
      } finally {
        setLoading(false);
      }
    };

    fetchInitialData();
  }, [matchId]);

  useEffect(() => {
    if (!meetingPlace || !mapContainerRef.current) return;

    setKakaoMapStatus('loading');

    let isCancelled = false;
    let retryCount = 0;
    let retryTimerId: number | undefined;
    let resizeObserver: ResizeObserver | undefined;
    const relayoutTimerIds: number[] = [];
    const maxRetryCount = 30;

    const getMapContainer = () => mapContainerRef.current;

    const hasUsableMapContainerSize = () => {
      const container = getMapContainer();
      if (!container) return false;

      const rect = container.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    };

    const scheduleMapRelayout = (map: any, center: any) => {
      [0, 120, 300].forEach((delay) => {
        const timerId = window.setTimeout(() => {
          if (isCancelled) return;
          (map as any).relayout?.();
          map.setCenter(center);
          radiusCircleRef.current?.setMap(map);
        }, delay);
        relayoutTimerIds.push(timerId);
      });
    };

    const initializeMap = (kakaoMaps: typeof kakao.maps) => {
      if (isCancelled || !mapContainerRef.current) return;

      if (!hasUsableMapContainerSize()) {
        retryCount += 1;
        if (retryCount > maxRetryCount) {
          setKakaoMapStatus('error');
          return;
        }
        retryTimerId = window.setTimeout(() => initializeMap(kakaoMaps), 100);
        return;
      }

      if (
        !Number.isFinite(meetingPlace.latitude)
        || !Number.isFinite(meetingPlace.longitude)
      ) {
        console.error('Invalid meeting place coordinates', meetingPlace);
        setKakaoMapStatus('error');
        return;
      }

      try {
        const container = mapContainerRef.current;
        const center = new kakaoMaps.LatLng(meetingPlace.latitude, meetingPlace.longitude);
        const map = new kakaoMaps.Map(container, { center, level: 3 });
        mapRef.current = map;

        radiusCircleRef.current?.setMap(null);
        radiusCircleRef.current = new kakaoMaps.Circle({
          center,
          radius: USER_VISIBLE_RADIUS_METERS,
          strokeWeight: 3,
          strokeColor: '#d84315',
          strokeOpacity: 0.95,
          strokeStyle: 'solid',
          fillColor: '#ff7043',
          fillOpacity: 0.25,
        });
        radiusCircleRef.current.setMap(map);
        setKakaoMapStatus('ready');

        scheduleMapRelayout(map, center);
        resizeObserver = new ResizeObserver(() => {
          if (isCancelled) return;
          (map as any).relayout?.();
          map.setCenter(center);
        });
        resizeObserver.observe(container);
      } catch (err) {
        console.error('Failed to initialize Kakao map', err);
        setKakaoMapStatus('error');
      }
    };

    try {
      loadKakaoMaps()
        .then((kakaoMaps) => {
          window.requestAnimationFrame(() => {
            if (!isCancelled) {
              initializeMap(kakaoMaps);
            }
          });
        })
        .catch((err) => {
          console.error('Failed to load Kakao map SDK', err);
          setKakaoMapStatus('error');
        });
    } catch (err) {
      console.error('Failed to initialize Kakao map', err);
      setKakaoMapStatus('error');
    }

    return () => {
      isCancelled = true;
      if (retryTimerId !== undefined) {
        window.clearTimeout(retryTimerId);
      }
      relayoutTimerIds.forEach((timerId) => window.clearTimeout(timerId));
      resizeObserver?.disconnect();
      radiusCircleRef.current?.setMap(null);
      radiusCircleRef.current = null;
      mapRef.current = null;
      myMarkerRef.current?.setMap(null);
      myMarkerRef.current = null;
      opponentMarkerRefs.current.forEach((marker) => marker.setMap(null));
      opponentMarkerRefs.current = [];
    };
  }, [meetingPlace, mapRetryKey]);

  useEffect(() => {
    if (!meetingPlace) return;

    if (!navigator.geolocation) {
      setLocationError('브라우저에서 위치 정보를 사용할 수 없습니다.');
      setIsLocating(false);
      return;
    }

    let isCancelled = false;
    setIsLocating(true);
    setLocationError(null);

    const applyCurrentPosition = (position: GeolocationPosition) => {
      if (isCancelled) return;
      setLocationError(null);
      setIsLocating(false);
      setCurrentPosition({ latitude: position.coords.latitude, longitude: position.coords.longitude });
    };

    navigator.geolocation.getCurrentPosition(
      applyCurrentPosition,
      (error) => {
        // Mac Chrome can fail the quick lookup while the high accuracy watcher still succeeds.
        console.warn(`Initial geolocation lookup failed (${error.code}): ${error.message}`, error);
      },
      {
        enableHighAccuracy: false,
        maximumAge: LOCATION_INITIAL_MAXIMUM_AGE_MS,
        timeout: LOCATION_INITIAL_TIMEOUT_MS,
      },
    );

    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        applyCurrentPosition(position);
      },
      (error) => {
        if (isCancelled) return;
        console.error(`Geolocation error (${error.code}): ${error.message}`, error);
        setLocationError(getGeolocationErrorMessage(error));
        setIsLocating(false);
      },
      {
        enableHighAccuracy: true,
        maximumAge: LOCATION_WATCH_MAXIMUM_AGE_MS,
        timeout: LOCATION_WATCH_TIMEOUT_MS,
      },
    );

    return () => {
      isCancelled = true;
      navigator.geolocation.clearWatch(watchId);
    };
  }, [meetingPlace, locationRetryKey]);

  useEffect(() => {
    if (!meetingPlace || !currentPosition) return;

    const nextDistance = calculateDistance(
      currentPosition.latitude,
      currentPosition.longitude,
      meetingPlace.latitude,
      meetingPlace.longitude,
    );
    setDistance(nextDistance);
    setIsWithinRange(nextDistance <= USER_VISIBLE_RADIUS_METERS);

    if (kakaoMapAvailable && mapRef.current && window.kakao?.maps) {
      const position = new window.kakao.maps.LatLng(currentPosition.latitude, currentPosition.longitude);
      if (!myMarkerRef.current) {
        myMarkerRef.current = createLocationOverlay({
          map: mapRef.current,
          position,
          color: '#4f7df3',
          label: '내 위치',
        });
      } else {
        myMarkerRef.current.setPosition(position);
      }
    }
  }, [currentPosition, meetingPlace, kakaoMapAvailable]);

  useEffect(() => {
    if (!matchId) return;

    const poll = async () => {
      try {
        const verificationRes = await getMeetVerification(matchId);
        applyVerificationData(verificationRes.data.data);

        if (currentPosition) {
          await updateMyLocation(matchId, currentPosition.latitude, currentPosition.longitude).catch(console.error);
          const locationRes = await getLocations(matchId);
          const locations = locationRes.data.data.opponentLocations?.length
            ? locationRes.data.data.opponentLocations
            : locationRes.data.data.opponentLocation
              ? [locationRes.data.data.opponentLocation]
              : [];

          if (kakaoMapAvailable && mapRef.current && window.kakao?.maps) {
            opponentMarkerRefs.current.forEach((marker) => marker.setMap(null));
            opponentMarkerRefs.current = locations.map((location) => createLocationOverlay({
              map: mapRef.current,
              position: new window.kakao.maps.LatLng(location.latitude, location.longitude),
              color: '#ff8a3d',
              label: '상대방 위치',
            }));
          }
        }
      } catch (err: any) {
        console.error('Failed to refresh verification status', err);
        const code = err.response?.data?.code;
        if (code === 'MATCH_002' || code === 'CHAT_002' || code === 'CHAT_004') {
          blockCancelledParticipant();
        }
      }
    };

    poll();
    const intervalId = window.setInterval(poll, 5000);
    return () => window.clearInterval(intervalId);
  }, [matchId, currentPosition, kakaoMapAvailable, isCurrentUserAuthor]);

  const handleVerify = async () => {
    if (!isWithinRange || !currentPosition) return;

    try {
      const res = await createPlaceVerification(matchId, {
        currentLat: currentPosition.latitude,
        currentLng: currentPosition.longitude,
      });
      setIsVerified(true);
      const verificationRes = await getMeetVerification(res.data.data.matchId);
      applyVerificationData(verificationRes.data.data);
    } catch (err: any) {
      alert(err.response?.data?.message || '장소 인증에 실패했습니다.');
    }
  };

  const handleBackToChat = () => {
    if (chatRoomId) {
      navigate(`/chat/${chatRoomId}`, { state: { matchId } });
      return;
    }
    navigate(`/matches/${matchId}`);
  };

  const handleRetryLocation = () => {
    setLocationError(null);
    setIsLocating(true);
    setLocationRetryKey((nextRetryKey) => nextRetryKey + 1);
  };

  if (loading || !meetingPlace) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="mb-4 animate-spin text-[#d84315]" size={40} />
        <p className="text-[#616161]">정보를 불러오는 중...</p>
      </div>
    );
  }

  const isMeetAfterQrFallbackTime = meetingPlace
    ? Date.now() >= new Date(meetingPlace.meetAt).getTime() + QR_FALLBACK_AFTER_MINUTES * 60 * 1000
    : false;

  const allPlaceVerified = authorVerified
    && verificationParticipants.length > 0
    && verificationParticipants.every((participant) => participant.verified);

  // 전원 GPS 완료 또는 만남 시간 +3분 이후 QR 단계 진입
  const isQrStepOpen = allPlaceVerified || isMeetAfterQrFallbackTime;
  const canShowQrStepButton = isQrStepOpen;
  const locationStatusLabel = currentPosition
    ? isWithinRange
      ? '범위 이내'
      : '범위 밖'
    : locationError
      ? '위치 확인 실패'
      : '위치 확인 중';
  const locationStatusColor = currentPosition
    ? isWithinRange
      ? 'text-[#4caf50]'
      : 'text-[#ef5350]'
    : locationError
      ? 'text-[#ef5350]'
      : 'text-[#616161]';

  const handleEnterQrStep = () => {
    if (!isVerified) {
      alert('장소 인증이 선행되어야 합니다.');
      return;
    }

    navigate(`/matches/${matchId}/qr`);
  };

  return (
    <div className="mx-auto w-full max-w-2xl p-0 sm:p-4">
      <button
        type="button"
        onClick={handleBackToChat}
        className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
      >
        <ArrowLeft size={18} />
        채팅방으로 돌아가기
      </button>

      <div className="rounded-2xl bg-white p-4 shadow-lg sm:p-8">
        <div className="mb-6 text-center">
          <div className="mb-3 flex items-center justify-center gap-2">
            <MapPin size={24} className="text-[#d84315]" />
            <h1 className="text-2xl font-bold text-[#212121]">장소 인증</h1>
          </div>
          <p className={`text-lg font-semibold ${locationStatusColor}`}>
            {locationStatusLabel}
          </p>
          <div className="mt-3 text-[#616161]">
            <p className="font-semibold text-[#212121]">{meetingPlace.name}</p>
            <p className="text-sm">약속 시간: {meetingPlace.time}</p>
          </div>
          {locationError && (
            <div className="mt-3">
              <p className="text-sm text-[#ef5350]">{locationError}</p>
              <button
                type="button"
                onClick={handleRetryLocation}
                disabled={isLocating}
                className="mt-2 inline-flex items-center justify-center rounded-lg border border-[#d84315] bg-white px-3 py-1.5 text-xs font-bold text-[#d84315] transition-colors hover:bg-[#fff3e0] disabled:cursor-wait disabled:border-[#bdbdbd] disabled:text-[#9e9e9e] disabled:hover:bg-white"
              >
                {isLocating ? '현재 위치 확인 중' : '현재 위치 다시 확인'}
              </button>
            </div>
          )}
        </div>

        {kakaoMapStatus !== 'error' ? (
          <div className="relative mb-3 h-56 w-full overflow-hidden rounded-2xl border-2 border-[#e0e0e0] bg-[#fafafa] sm:h-64">
            <div ref={mapContainerRef} className="hankki-kakao-map h-full w-full" />
            {kakaoMapStatus === 'loading' && (
              <div className="absolute inset-0 flex items-center justify-center bg-[#fafafa]">
                <div className="text-center">
                  <Loader2 size={34} className="mx-auto mb-3 animate-spin text-[#d84315]" />
                  <p className="text-xs font-semibold text-[#9e9e9e]">지도를 불러오는 중입니다.</p>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="relative mb-6 rounded-2xl border-2 border-[#e0e0e0] bg-[#fafafa] p-8">
            <div ref={mapContainerRef} className="hidden h-56 w-full sm:h-64" />
            <div className="mb-4 rounded-lg border border-[#ff9800] bg-[#fff3e0] px-3 py-2 text-center">
              <p className="text-xs text-[#e65100]">지도를 불러오지 못했습니다. 거리 기반 인증은 정상 작동합니다.</p>
            </div>
            <div className="flex h-40 items-center justify-center sm:h-48">
              <div className="text-center">
                <Navigation size={40} className="mx-auto mb-2 animate-pulse text-[#d84315]" />
                <p className="text-xs text-[#9e9e9e]">실시간 위치 추적 중</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                setKakaoMapStatus('loading');
                setMapRetryKey((nextRetryKey) => nextRetryKey + 1);
              }}
              className="mt-4 w-full rounded-lg border border-[#d84315] bg-white px-3 py-2 text-sm font-bold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
            >
              지도 다시 불러오기
            </button>
          </div>
        )}

        {distance !== null && (
          <div className="mb-6">
            <div className="mb-3 flex flex-wrap items-center justify-center gap-3 text-xs font-semibold text-[#616161] sm:gap-5">
              <LegendDot color="#4f7df3" label="나" />
              <LegendDot color="#ff8a3d" label="상대방" />
            </div>
            <div className="mb-2 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
              <span className="text-sm text-[#616161]">현재 거리</span>
              <span className={`text-lg font-bold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
                {formatDistance(distance)} / {formatDistance(USER_VISIBLE_RADIUS_METERS)}
              </span>
            </div>
            <div className="relative h-3 w-full overflow-hidden rounded-full bg-[#e0e0e0]">
              <div
                className={`h-full transition-all duration-300 ${isWithinRange ? 'bg-[#4caf50]' : 'bg-[#ef5350]'}`}
                style={{ width: `${Math.min((distance / USER_VISIBLE_RADIUS_METERS) * 100, 100)}%` }}
              />
            </div>
          </div>
        )}

        <div className="mb-6 rounded-xl border border-[#e0e0e0] bg-gradient-to-br from-[#f5f5f5] to-white p-5">
          <h3 className="mb-4 font-semibold text-[#212121]">장소 인증 현황</h3>
          <div className="space-y-3">
            <VerificationRow name={authorNickname} role="등록자" verified={authorVerified} />
            {verificationParticipants.length > 0 ? (
              verificationParticipants.map((participant) => (
                <VerificationRow
                  key={participant.matchId}
                  name={participant.nickname}
                  role="신청자"
                  verified={participant.verified}
                />
              ))
            ) : (
              <div className="rounded-lg border border-dashed border-[#e0e0e0] bg-white p-3 text-sm text-[#9e9e9e]">
                아직 참여자 정보가 없습니다.
              </div>
            )}
          </div>
        </div>

        <div className="space-y-3">
          {canShowQrStepButton && (
            <button
              onClick={handleEnterQrStep}
              className="w-full rounded-xl bg-[#4caf50] py-4 text-lg font-bold text-white shadow-md transition-all hover:bg-[#43a047]"
            >
              QR 인증 단계로 이동
            </button>
          )}

          {!isVerified ? (
            <button
              onClick={handleVerify}
              disabled={!isWithinRange}
              className={`w-full rounded-xl py-4 text-lg font-bold shadow-md transition-all ${
                isWithinRange ? 'bg-[#d84315] text-white hover:bg-[#bf360c]' : 'cursor-not-allowed bg-[#e0e0e0] text-[#9e9e9e]'
              }`}
            >
              장소 인증하기
            </button>
          ) : (
            <div className="rounded-xl border border-[#4caf50] bg-[#e8f5e9] px-4 py-4 text-center">
              <p className="font-semibold text-[#2e7d32]">장소 인증 완료! 다른 참여자를 기다려주세요.</p>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}

function createLocationOverlay({
  map,
  position,
  color,
  label,
}: {
  map: any;
  position: any;
  color: string;
  label: string;
}) {
  const markerElement = document.createElement('div');
  markerElement.setAttribute('aria-label', label);
  markerElement.style.width = '14px';
  markerElement.style.height = '14px';
  markerElement.style.borderRadius = '9999px';
  markerElement.style.backgroundColor = color;
  markerElement.style.border = '2px solid #ffffff';
  markerElement.style.boxShadow = '0 1px 4px rgba(33, 33, 33, 0.28)';
  markerElement.style.boxSizing = 'border-box';

  return new window.kakao.maps.CustomOverlay({
    map,
    position,
    content: markerElement,
    xAnchor: 0.5,
    yAnchor: 0.5,
  });
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className="inline-block h-3 w-3 shrink-0 rounded-full border-2 border-white shadow-sm"
        style={{ backgroundColor: color }}
      />
      <span>{label}</span>
    </span>
  );
}

function VerificationRow({ name, role, verified }: { name: string; role: string; verified: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg bg-white p-3">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-[#212121]">{name || '알 수 없음'}</p>
        <p className="text-xs font-semibold text-[#9e9e9e]">{role}</p>
      </div>
      <span className={`hankki-status-badge shrink-0 rounded-full px-3 py-1 text-xs font-bold ${verified ? 'bg-[#e8f5e9] text-[#2e7d32]' : 'bg-[#f5f5f5] text-[#757575]'}`}>
        {verified ? '완료' : '대기'}
      </span>
    </div>
  );
}

function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number) {
  const earthRadius = 6371e3;
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(deltaPhi / 2) ** 2 + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(distanceMeters: number) {
  if (distanceMeters >= 1000) {
    return `${(distanceMeters / 1000).toFixed(1)}km`;
  }

  return `${distanceMeters.toFixed(1)}m`;
}

function getGeolocationErrorMessage(error: GeolocationPositionError) {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return '위치 권한이 차단되었습니다. 브라우저와 운영체제의 위치 권한을 모두 허용해주세요.';
    case error.POSITION_UNAVAILABLE:
      return '현재 위치를 계산할 수 없습니다. Wi-Fi 또는 네트워크 연결과 기기의 위치 서비스를 확인해주세요.';
    case error.TIMEOUT:
      return '현재 위치 확인 시간이 초과되었습니다. 잠시 후 다시 시도하거나 위치 서비스 상태를 확인해주세요.';
    default:
      return '현재 위치를 가져오지 못했습니다. 위치 서비스 상태를 확인해주세요.';
  }
}
