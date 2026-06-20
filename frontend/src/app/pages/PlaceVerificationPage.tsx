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

declare global {
  interface Window {
    kakao?: any;
  }
}

interface Position {
  latitude: number;
  longitude: number;
}

const USER_VISIBLE_RADIUS_METERS = 50;

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
  const [useSimulation, setUseSimulation] = useState(false);
  const [kakaoMapAvailable, setKakaoMapAvailable] = useState(true);
  const [authorNickname, setAuthorNickname] = useState('등록자');
  const [authorVerified, setAuthorVerified] = useState(false);
  const [verificationParticipants, setVerificationParticipants] = useState<ParticipantVerification[]>([]);
  const [isCurrentUserAuthor, setIsCurrentUserAuthor] = useState<boolean | null>(null);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const radiusCircleRef = useRef<any>(null);
  const myMarkerRef = useRef<any>(null);
  const opponentMarkerRefs = useRef<any[]>([]);

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

    let isCancelled = false;
    let retryCount = 0;
    let retryTimerId: number | undefined;
    const maxRetryCount = 30;

    const isKakaoMapReady = () =>
      Boolean(
        window.kakao?.maps?.LatLng &&
        window.kakao?.maps?.Map &&
        window.kakao?.maps?.Circle,
      );

    const initializeMap = () => {
      if (isCancelled || !mapContainerRef.current || !isKakaoMapReady()) return;

      try {
        const center = new window.kakao.maps.LatLng(meetingPlace.latitude, meetingPlace.longitude);
        const map = new window.kakao.maps.Map(mapContainerRef.current, { center, level: 3 });
        mapRef.current = map;

        radiusCircleRef.current?.setMap(null);
        radiusCircleRef.current = new window.kakao.maps.Circle({
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
        setKakaoMapAvailable(true);

        window.setTimeout(() => {
          map.relayout?.();
          map.setCenter(center);
          radiusCircleRef.current?.setMap(map);
        }, 0);
      } catch (err) {
        console.error('Failed to initialize Kakao map', err);
        setKakaoMapAvailable(false);
      }
    };

    const initializeWhenSdkReady = () => {
      if (isCancelled) return;

      if (!isKakaoMapReady()) {
        retryCount += 1;
        if (retryCount > maxRetryCount) {
          setKakaoMapAvailable(false);
          return;
        }
        retryTimerId = window.setTimeout(initializeWhenSdkReady, 100);
        return;
      }

      initializeMap();
    };

    try {
      initializeWhenSdkReady();
    } catch (err) {
      console.error('Failed to initialize Kakao map', err);
      setKakaoMapAvailable(false);
    }

    return () => {
      isCancelled = true;
      if (retryTimerId !== undefined) {
        window.clearTimeout(retryTimerId);
      }
      radiusCircleRef.current?.setMap(null);
      radiusCircleRef.current = null;
      mapRef.current = null;
      myMarkerRef.current?.setMap(null);
      myMarkerRef.current = null;
      opponentMarkerRefs.current.forEach((marker) => marker.setMap(null));
      opponentMarkerRefs.current = [];
    };
  }, [meetingPlace]);

  useEffect(() => {
    if (!meetingPlace) return;

    if (useSimulation) {
      setCurrentPosition({ latitude: meetingPlace.latitude, longitude: meetingPlace.longitude });
      return;
    }

    if (!navigator.geolocation) {
      setLocationError('브라우저에서 위치 정보를 사용할 수 없습니다.');
      return;
    }

    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        setLocationError(null);
        setCurrentPosition({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      },
      () => setLocationError('현재 위치를 가져오지 못했습니다. 위치 권한을 확인해주세요.'),
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 },
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [meetingPlace, useSimulation]);

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
    const intervalId = window.setInterval(poll, 3000);
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

  if (loading || !meetingPlace) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="mb-4 animate-spin text-[#d84315]" size={40} />
        <p className="text-[#616161]">정보를 불러오는 중...</p>
      </div>
    );
  }

  const isMeetAfterQrFallbackTime = meetingPlace
    ? Date.now() >= new Date(meetingPlace.meetAt).getTime() + 10 * 60 * 1000
    : false;

  const allPlaceVerified = authorVerified
    && verificationParticipants.length > 0
    && verificationParticipants.every((participant) => participant.verified);

  // QR step opens only after author GPS and either all GPS completion or fallback time.
  const isQrStepOpen = authorVerified && (allPlaceVerified || isMeetAfterQrFallbackTime);
  const canEnterQrStep = isVerified && isQrStepOpen;

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
          <p className={`text-lg font-semibold ${isWithinRange ? 'text-[#4caf50]' : 'text-[#ef5350]'}`}>
            {isWithinRange ? '범위 이내' : '범위 밖'}
          </p>
          <div className="mt-3 text-[#616161]">
            <p className="font-semibold text-[#212121]">{meetingPlace.name}</p>
            <p className="text-sm">약속 시간: {meetingPlace.time}</p>
          </div>
          {locationError && <p className="mt-2 text-sm text-[#ef5350]">{locationError}</p>}
        </div>

        {kakaoMapAvailable ? (
          <div ref={mapContainerRef} className="mb-3 h-56 w-full overflow-hidden rounded-2xl border-2 border-[#e0e0e0] sm:h-64" />
        ) : (
          <div className="relative mb-6 rounded-2xl border-2 border-[#e0e0e0] bg-[#fafafa] p-8">
            <div className="mb-4 rounded-lg border border-[#ff9800] bg-[#fff3e0] px-3 py-2 text-center">
              <p className="text-xs text-[#e65100]">지도를 불러오지 못했습니다. 거리 기반 인증은 정상 작동합니다.</p>
            </div>
            <div className="flex h-40 items-center justify-center sm:h-48">
              <div className="text-center">
                <Navigation size={40} className="mx-auto mb-2 animate-pulse text-[#d84315]" />
                <p className="text-xs text-[#9e9e9e]">실시간 위치 추적 중</p>
              </div>
            </div>
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
                {distance.toFixed(1)}m / {USER_VISIBLE_RADIUS_METERS}m
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

        {canEnterQrStep ? (
          <button
            onClick={() => navigate(`/matches/${matchId}/qr`)}
            className="w-full rounded-xl bg-[#4caf50] py-4 text-lg font-bold text-white shadow-md transition-all hover:bg-[#43a047]"
          >
            QR 인증 단계로 이동
          </button>
        ) : !isVerified ? (
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

        <div className="mt-6 border-t border-[#e0e0e0] pt-4">
          <label className="flex cursor-pointer items-center gap-2">
            <input
              type="checkbox"
              checked={useSimulation}
              onChange={(event) => setUseSimulation(event.target.checked)}
              className="h-4 w-4"
            />
            <span className="text-xs text-[#ef6c00]">테스트 모드</span>
          </label>
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
      <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-bold ${verified ? 'bg-[#e8f5e9] text-[#2e7d32]' : 'bg-[#f5f5f5] text-[#757575]'}`}>
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
