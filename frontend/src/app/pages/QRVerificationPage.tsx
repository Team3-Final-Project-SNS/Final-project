import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { Camera, Check, Loader2, MessageCircle } from 'lucide-react';
import QRCode from 'qrcode';
import jsQR from 'jsqr';
import { createQrScan, getMeetQrByPost, getMeetVerification, ParticipantVerification } from '../../api/meetApi';
import { getMatchDetail, getMyMatches } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';

type QrRole = 'author' | 'applicant';
const CAMERA_REQUEST_TIMEOUT_MS = 15000;

const getQrBaseUrl = () => {
  const configuredUrl = import.meta.env.VITE_QR_BASE_URL;
  return configuredUrl ? configuredUrl.replace(/\/$/, '') : window.location.origin;
};

const isMeetVerified = (participant: ParticipantVerification) =>
  participant.meetVerified || participant.verificationStatus === 'DONE';

// QR 만남 인증 현황에는 GPS 장소 인증을 완료한 신청자만 포함한다.
// 장소 미인증자는 노쇼 가능성이 있으므로 QR 완료 대기 대상에서 제외한다.
const isQrParticipant = (participant: ParticipantVerification) => participant.verified;

async function findMyMatchIdByPostId(postId: number) {
  let page = 0;
  const size = 100;

  while (page < 5) {
    const response = await getMyMatches(undefined, page, size);
    const data = response.data.data;
    const match = data.content.find((item) => item.postId === postId && item.status === 'MATCHED');
    if (match) return match.matchId;
    if (!data.hasNext) break;
    page += 1;
  }

  return null;
}

async function getUserMediaWithTimeout(constraints: MediaStreamConstraints, timeoutMs: number) {
  let timeoutId: number | undefined;
  let didTimeout = false;

  const mediaRequest = navigator.mediaDevices.getUserMedia(constraints).then((stream) => {
    if (didTimeout) {
      stream.getTracks().forEach((track) => track.stop());
      throw new Error('Camera request timed out.');
    }
    return stream;
  });

  const timeout = new Promise<MediaStream>((_, reject) => {
    timeoutId = window.setTimeout(() => {
      didTimeout = true;
      reject(new Error('Camera request timed out.'));
    }, timeoutMs);
  });

  return Promise.race([mediaRequest, timeout]).finally(() => {
    if (timeoutId !== undefined) {
      window.clearTimeout(timeoutId);
    }
  });
}

export default function QRVerificationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const routeMatchId = Number(id);
  const roleParam = searchParams.get('role');
  const tokenFromUrl = searchParams.get('qrToken');
  const postIdFromUrl = searchParams.get('postId');

  const [role, setRole] = useState<QrRole | null>(null);
  const [postId, setPostId] = useState<number | null>(postIdFromUrl ? Number(postIdFromUrl) : null);
  const [resolvedMatchId, setResolvedMatchId] = useState(routeMatchId);
  const [step, setStep] = useState<'display' | 'scan' | 'success'>('display');
  const [qrToken, setQrToken] = useState('');
  const [qrImageUrl, setQrImageUrl] = useState('');
  const [qrImageError, setQrImageError] = useState('');
  const [qrImageRetryKey, setQrImageRetryKey] = useState(0);
  const [qrInput, setQrInput] = useState(tokenFromUrl || '');
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [scanError, setScanError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const [cameraReady, setCameraReady] = useState(false);
  const [authorNickname, setAuthorNickname] = useState('등록자');
  const [verificationParticipants, setVerificationParticipants] = useState<ParticipantVerification[]>([]);
  const [chatRoomId, setChatRoomId] = useState<number | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scanFrameRef = useRef<number | null>(null);
  const scannedTokenRef = useRef('');

  const stopCamera = () => {
    if (scanFrameRef.current !== null) {
      cancelAnimationFrame(scanFrameRef.current);
      scanFrameRef.current = null;
    }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraReady(false);
  };

  const blockCancelledParticipant = () => {
    alert('매칭 취소자는 인증 페이지에 접근할 수 없습니다.');
    navigate('/matches', { replace: true });
  };

  const ensureApplicantPlaceVerified = async (targetMatchId: number) => {
    const verificationRes = await getMeetVerification(targetMatchId);
    const participants = verificationRes.data.data.participants || [];
    const myParticipant = participants.find((participant) => participant.matchId === targetMatchId);

    // 신청자는 GPS 장소 인증을 먼저 완료해야 QR 스캔 화면으로 진입할 수 있다.
    if (!myParticipant?.verified) {
      alert('GPS 장소 인증을 먼저 완료해야 QR 인증을 진행할 수 있습니다.');
      navigate(`/matches/${targetMatchId}/place-verification`, { replace: true });
      return false;
    }

    return true;
  };

  const ensureAuthorPlaceVerified = async (targetMatchId: number) => {
    const verificationRes = await getMeetVerification(targetMatchId);

    // 등록자도 GPS 장소 인증을 먼저 완료해야 QR 표시 화면으로 진입할 수 있다.
    if (!verificationRes.data.data.authorPlaceVerifiedAt) {
      alert('GPS 장소 인증을 먼저 완료해야 QR 인증을 진행할 수 있습니다.');
      navigate(`/matches/${targetMatchId}/place-verification`, { replace: true });
      return false;
    }

    return true;
  };

  useEffect(() => {
    const resolveRole = async () => {
      if (!routeMatchId) return;

      try {
        setLoading(true);

        if (roleParam === 'applicant' && postIdFromUrl) {
          const ownMatchId = await findMyMatchIdByPostId(Number(postIdFromUrl));
          if (ownMatchId) {
            const matchRes = await getMatchDetail(ownMatchId);
            const canScanQr = await ensureApplicantPlaceVerified(ownMatchId);
            if (!canScanQr) return;

            setResolvedMatchId(ownMatchId);
            setPostId(Number(postIdFromUrl));
            setChatRoomId(matchRes.data.data.chatRoomId);
            setRole('applicant');
            setStep('scan');
            return;
          }
        }

        const [matchRes, userRes] = await Promise.all([getMatchDetail(routeMatchId), getUserMe()]);
        const match = matchRes.data.data;
        const currentUserId = userRes.data.data.userId;

        setResolvedMatchId(routeMatchId);
        setPostId(match.postId);
        setChatRoomId(match.chatRoomId);

        if (roleParam === 'author' || roleParam === 'applicant') {
          if (roleParam === 'applicant') {
            const canScanQr = await ensureApplicantPlaceVerified(routeMatchId);
            if (!canScanQr) return;
          } else {
            const canShowQr = await ensureAuthorPlaceVerified(routeMatchId);
            if (!canShowQr) return;
          }

          setRole(roleParam);
          setStep(roleParam === 'applicant' ? 'scan' : 'display');
        } else {
          const nextRole = currentUserId === match.authorId ? 'author' : 'applicant';
          if (nextRole === 'applicant') {
            const canScanQr = await ensureApplicantPlaceVerified(routeMatchId);
            if (!canScanQr) return;
          } else {
            const canShowQr = await ensureAuthorPlaceVerified(routeMatchId);
            if (!canShowQr) return;
          }

          setRole(nextRole);
          setStep(nextRole === 'applicant' ? 'scan' : 'display');
        }
      } catch (err: any) {
        console.error('QR 인증 정보 확인 실패:', err);
        const code = err.response?.data?.code;
        if (code === 'MATCH_002' || code === 'CHAT_002' || code === 'CHAT_004' || code === 'CHAT_007') {
          blockCancelledParticipant();
          return;
        }
        alert('매칭 정보를 확인할 수 없습니다.');
        navigate('/matches');
      } finally {
        setLoading(false);
      }
    };

    resolveRole();
  }, [navigate, postIdFromUrl, roleParam, routeMatchId]);

  useEffect(() => {
    if (role !== 'author' || !postId) return;

    const fetchQr = async () => {
      try {
        setLoading(true);
        const res = await getMeetQrByPost(postId);
        const data = res.data.data;
        setQrToken(data.qrToken);

        const remainingSeconds = Math.floor((new Date(data.qrExpiresAt).getTime() - Date.now()) / 1000);
        setTimeRemaining(Math.max(0, remainingSeconds));
      } catch (err: any) {
        console.error('QR 발급 실패:', err.response?.data);
        alert(err.response?.data?.message || 'QR 발급에 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchQr();
  }, [postId, role]);

  useEffect(() => {
    if (!qrToken || !postId) return;

    const generateQrImage = async () => {
      try {
        setQrImageError('');
        setQrImageUrl('');
        const qrUrl = `${getQrBaseUrl()}/matches/${resolvedMatchId}/qr?role=applicant&postId=${postId}&qrToken=${encodeURIComponent(qrToken)}`;
        const imageUrl = await QRCode.toDataURL(qrUrl, {
          width: 256,
          margin: 2,
          color: { dark: '#212121', light: '#ffffff' },
        });
        setQrImageUrl(imageUrl);
      } catch (err) {
        console.error('QR image generation failed:', err);
        setQrImageError('QR 이미지를 생성하지 못했습니다. 다시 시도해주세요.');
      }
    };

    generateQrImage().catch((err) => console.error('QR 이미지 생성 실패:', err));
  }, [postId, qrImageRetryKey, qrToken, resolvedMatchId]);

  useEffect(() => {
    if (timeRemaining <= 0) return;

    const timer = window.setInterval(() => {
      setTimeRemaining((prev) => Math.max(0, prev - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [timeRemaining]);

  useEffect(() => {
    // 등록자 화면만 현황을 폴링하고, 신청자는 QR 성공 즉시 내 매칭으로 이동
    const shouldPollStatus = !!resolvedMatchId && role === 'author' && !!qrToken;

    if (!shouldPollStatus) return;

    const intervalId = window.setInterval(async () => {
      try {
        const res = await getMeetVerification(resolvedMatchId);
        const data = res.data.data;
        const participants = (data.participants || []).filter(isQrParticipant);
        setAuthorNickname(data.authorNickname || '등록자');
        setVerificationParticipants(participants);

        if (participants.length > 0 && participants.every(isMeetVerified)) {
          setStep('success');
          window.clearInterval(intervalId);
          window.setTimeout(() => navigate('/matches'), 2000);
        }
      } catch (err) {
        console.error('인증 상태 조회 실패:', err);
      }
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [navigate, qrToken, resolvedMatchId, role, step]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const extractQrToken = (value: string) => {
    try {
      const url = new URL(value);
      return url.searchParams.get('qrToken') || value;
    } catch {
      const tokenMatch = value.match(/[?&]qrToken=([^&]+)/);
      return tokenMatch ? decodeURIComponent(tokenMatch[1]) : value;
    }
  };

  const handleScan = async (tokenOverride?: string) => {
    const tokenToScan = extractQrToken(tokenOverride ?? qrInput).trim();
    if (!tokenToScan) {
      setScanError('QR 토큰을 입력하거나 QR 코드를 스캔해주세요.');
      return;
    }

    try {
      setLoading(true);
      setScanError('');
      // 신청자 QR 인증은 단건 완료 처리이므로 대기 화면 없이 내 매칭으로 이동
      await createQrScan(resolvedMatchId, tokenToScan);
      navigate('/matches');
    } catch (err: any) {
      setScanError(err.response?.data?.message || 'QR 인증에 실패했습니다.');
      scannedTokenRef.current = '';
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (role !== 'applicant') return;
    const token = tokenFromUrl ? extractQrToken(tokenFromUrl) : '';
    if (token) {
      setQrInput(token);
      setStep('scan');
    }
  }, [role, tokenFromUrl]);

  useEffect(() => {
    if (role !== 'applicant' || step !== 'scan') {
      stopCamera();
      return;
    }

    let stopped = false;

    const startCameraScanner = async () => {
      try {
        setCameraError('');
        if (!navigator.mediaDevices?.getUserMedia) {
          setCameraError('이 브라우저에서는 카메라를 사용할 수 없습니다.');
          return;
        }

        const stream = await getUserMediaWithTimeout({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        }, CAMERA_REQUEST_TIMEOUT_MS);

        if (stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
          setCameraReady(true);
        }

        const scanFrame = async () => {
          if (stopped || !videoRef.current) return;

          const video = videoRef.current;
          if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth > 0 && video.videoHeight > 0) {
            const canvas = canvasRef.current ?? document.createElement('canvas');
            canvasRef.current = canvas;
            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;

            const context = canvas.getContext('2d', { willReadFrequently: true });
            context?.drawImage(video, 0, 0, canvas.width, canvas.height);
            const imageData = context?.getImageData(0, 0, canvas.width, canvas.height);
            const qrCode = imageData ? jsQR(imageData.data, imageData.width, imageData.height) : null;
            const token = qrCode?.data ? extractQrToken(qrCode.data) : '';

            if (token && scannedTokenRef.current !== token) {
              scannedTokenRef.current = token;
              setQrInput(token);
              stopCamera();
              await handleScan(token);
              return;
            }
          }

          scanFrameRef.current = requestAnimationFrame(scanFrame);
        };

        scanFrameRef.current = requestAnimationFrame(scanFrame);
      } catch (err) {
        console.error('Camera start failed', err);
        setCameraError('카메라를 열 수 없습니다. 브라우저 카메라 권한을 허용해주세요.');
      }
    };

    startCameraScanner();

    return () => {
      stopped = true;
      stopCamera();
    };
  }, [resolvedMatchId, role, step]);

  const getParticipantBadge = (participant: ParticipantVerification) => {
    if (isMeetVerified(participant)) {
      return { label: 'QR 인증 완료', className: 'bg-[#e8f5e9] text-[#2e7d32]' };
    }
    if (participant.verified) {
      return { label: 'QR 대기', className: 'bg-[#fff3e0] text-[#ef6c00]' };
    }
    return { label: '장소 미인증', className: 'bg-[#f5f5f5] text-[#757575]' };
  };

  const renderChatReturnButton = () => {
    if (!chatRoomId) return null;

    return (
      <button
        type="button"
        onClick={() => navigate(`/chat/${chatRoomId}`, { state: { matchId: resolvedMatchId } })}
        className="mt-6 flex w-full items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] px-4 py-3 text-sm font-bold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
      >
        <MessageCircle size={18} />
        채팅으로 돌아가기
      </button>
    );
  };

  if (!role) {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="rounded-2xl bg-white p-8 text-center text-[#9e9e9e] shadow-lg">
          QR 인증 정보를 확인하는 중...
        </div>
      </div>
    );
  }

  if (role === 'applicant') {
    return (
      <div className="mx-auto w-full max-w-2xl">
        <div className="rounded-2xl bg-white p-4 shadow-lg sm:p-8">
          {step === 'scan' && (
            <>
              <h1 className="mb-2 text-center text-2xl font-bold text-[#212121]">QR 코드 인증</h1>
              <p className="mb-8 text-center text-[#616161]">
                등록자가 보여주는 QR 코드를 스캔하거나, 아래 인증번호를 확인해주세요.
              </p>

              <div className="relative mb-6 overflow-hidden rounded-2xl border-2 border-dashed border-[#e0e0e0] bg-[#111111]">
                <video ref={videoRef} autoPlay muted playsInline className="aspect-square w-full object-cover" />
                {!cameraReady && !cameraError && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#fafafa] text-center">
                    <Camera size={64} className="mx-auto mb-4 text-[#bdbdbd]" />
                    <p className="text-[#9e9e9e]">카메라를 여는 중...</p>
                  </div>
                )}
                {cameraReady && <div className="pointer-events-none absolute inset-8 rounded-2xl border-4 border-white/80 shadow-[0_0_0_999px_rgba(0,0,0,0.25)]" />}
                {cameraError && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#fafafa] p-8 text-center">
                    <Camera size={64} className="mx-auto mb-4 text-[#bdbdbd]" />
                    <p className="text-sm font-semibold text-[#757575]">{cameraError}</p>
                  </div>
                )}
              </div>

              <div className="mb-6 text-center">
                <p className="mb-3 text-sm text-[#9e9e9e]">인증번호 직접 입력</p>
                <div className="mx-auto flex max-w-md flex-col gap-2 sm:flex-row">
                  <input
                    type="text"
                    value={qrInput}
                    onChange={(event) => setQrInput(event.target.value)}
                    placeholder="hp_qr_..."
                    className="min-w-0 flex-1 rounded-lg border border-[#e0e0e0] px-4 py-3 focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                  />
                  <button
                    type="button"
                    onClick={() => handleScan()}
                    disabled={loading}
                    className="rounded-lg bg-[#d84315] px-6 py-3 font-semibold text-white transition-colors hover:bg-[#bf360c] disabled:opacity-50"
                  >
                    {loading ? '확인 중...' : '인증하기'}
                  </button>
                </div>
                {tokenFromUrl && (
                  <p className="mt-2 text-xs text-[#757575]">
                    QR 링크로 들어왔습니다. 인증하려면 버튼을 눌러주세요.
                  </p>
                )}
              </div>

              {scanError && (
                <div className="rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-center">
                  <span className="text-sm text-[#c62828]">{scanError}</span>
                </div>
              )}
            </>
          )}

          {false && (
            <div className="py-8">
              <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#fff3e0]">
                <Loader2 size={42} className="animate-spin text-[#ef6c00]" />
              </div>
              <h2 className="mb-3 text-center text-2xl font-bold text-[#212121]">QR 인증 완료</h2>
              <p className="mb-6 text-center text-[#616161]">
                모든 신청자의 QR 인증이 끝나면 만남 완료로 처리됩니다.
              </p>

              <div className="rounded-xl border border-[#e0e0e0] bg-[#fafafa] p-4">
                <h3 className="mb-3 text-sm font-bold text-[#212121]">만남 인증 현황</h3>
                <div className="space-y-2">
                  {verificationParticipants.length > 0 ? (
                    verificationParticipants.map((participant) => {
                      const badge = getParticipantBadge(participant);
                      return (
                        <div key={participant.matchId} className="flex flex-col gap-2 rounded-lg bg-white px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
                          <div>
                            <p className="text-sm font-semibold text-[#212121]">{participant.nickname || '알 수 없음'}</p>
                            <p className="text-xs text-[#9e9e9e]">신청자</p>
                          </div>
                          <span className={`rounded-full px-3 py-1 text-xs font-bold ${badge.className}`}>
                            {badge.label}
                          </span>
                        </div>
                      );
                    })
                  ) : (
                    <div className="rounded-lg border border-dashed border-[#e0e0e0] bg-white p-3 text-center text-sm text-[#9e9e9e]">
                      인증 현황을 불러오는 중입니다.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {step === 'success' && (
            <div className="py-12 text-center">
              <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#4caf50]">
                <Check size={48} className="text-white" />
              </div>
              <h2 className="mb-3 text-2xl font-bold text-[#212121]">만남 인증 완료</h2>
              <p className="mb-4 text-[#616161]">모든 신청자의 QR 인증이 완료되었습니다.</p>
              <div className="inline-block rounded-lg border border-[#4caf50] bg-[#e8f5e9] px-4 py-3">
                <span className="text-sm font-semibold text-[#2e7d32]">책임비 반환 처리 완료</span>
              </div>
            </div>
          )}
          {renderChatReturnButton()}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-2xl">
      <div className="rounded-2xl bg-white p-4 shadow-lg sm:p-8">
        <h1 className="mb-2 text-center text-2xl font-bold text-[#212121]">QR 코드 표시</h1>
        <p className="mb-8 text-center text-[#616161]">
          신청자에게 QR 코드를 보여주세요.
          <br />
          신청자가 인증을 완료하면 아래 현황이 갱신됩니다.
        </p>

        {loading ? (
          <div className="flex items-center justify-center gap-2 py-12 text-[#9e9e9e]">
            <Loader2 size={18} className="animate-spin" />
            QR 코드 발급 중...
          </div>
        ) : (
          <>
            <div className="mb-6 flex justify-center">
              {qrImageUrl ? (
                <img src={qrImageUrl} alt="만남 인증 QR 코드" className="w-full max-w-64 rounded-xl border border-[#e0e0e0]" />
              ) : qrImageError ? (
                <div className="flex h-64 w-64 flex-col items-center justify-center gap-3 rounded-xl border border-[#ff9800] bg-[#fff3e0] p-4 text-center">
                  <p className="text-sm font-semibold text-[#e65100]">{qrImageError}</p>
                  <button
                    type="button"
                    onClick={() => setQrImageRetryKey((nextRetryKey) => nextRetryKey + 1)}
                    className="rounded-lg border border-[#d84315] bg-white px-3 py-2 text-sm font-bold text-[#d84315] transition-colors hover:bg-[#fff8f3]"
                  >
                    QR 다시 생성
                  </button>
                </div>
              ) : (
                <div className="flex h-64 w-64 items-center justify-center rounded-xl bg-[#f5f5f5]">
                  <p className="text-sm text-[#9e9e9e]">QR 생성 중...</p>
                </div>
              )}
            </div>

            <div className="mb-6 rounded-xl border border-[#e0e0e0] bg-[#fafafa] p-4">
              <h3 className="mb-3 text-sm font-bold text-[#212121]">만남 인증 현황</h3>
              <div className="mb-2 flex items-center justify-between rounded-lg bg-white px-3 py-2">
                <div>
                  <p className="text-sm font-semibold text-[#212121]">{authorNickname}</p>
                  <p className="text-xs text-[#9e9e9e]">등록자</p>
                </div>
                <span className="rounded-full bg-[#e8f5e9] px-3 py-1 text-xs font-bold text-[#2e7d32]">QR 표시 중</span>
              </div>

              <div className="space-y-2">
                {verificationParticipants.length > 0 ? (
                  verificationParticipants.filter(isQrParticipant).map((participant) => {
                    const badge = getParticipantBadge(participant);
                    return (
                      <div key={participant.matchId} className="flex flex-col gap-2 rounded-lg bg-white px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="text-sm font-semibold text-[#212121]">{participant.nickname || '알 수 없음'}</p>
                          <p className="text-xs text-[#9e9e9e]">신청자</p>
                        </div>
                        <span className={`rounded-full px-3 py-1 text-xs font-bold ${badge.className}`}>
                          {badge.label}
                        </span>
                      </div>
                    );
                  })
                ) : (
                  <div className="rounded-lg border border-dashed border-[#e0e0e0] bg-white p-3 text-center text-sm text-[#9e9e9e]">
                    아직 참여자 정보가 없습니다.
                  </div>
                )}
              </div>
            </div>

            <div className="mb-6 text-center">
              <p className="mb-2 text-sm text-[#9e9e9e]">유효시간</p>
              <p className={`text-4xl font-bold ${timeRemaining < 60 ? 'text-[#ef5350]' : 'text-[#d84315]'}`}>
                {formatTime(timeRemaining)}
              </p>
            </div>
          </>
        )}

        {step === 'success' ? (
          <div className="py-8 text-center">
            <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#4caf50]">
              <Check size={48} className="text-white" />
            </div>
            <h2 className="mb-3 text-2xl font-bold text-[#212121]">만남 인증 완료</h2>
            <p className="text-[#616161]">모든 신청자의 QR 인증이 완료되었습니다.</p>
          </div>
        ) : (
          <div className="rounded-lg border border-[#ff9800] bg-[#fff3e0] p-4">
            <p className="text-sm text-[#ef6c00]">
              신청자가 QR을 스캔하고 인증을 누르면 만남 인증이 완료됩니다.
            </p>
          </div>
        )}
        {renderChatReturnButton()}
      </div>
    </div>
  );
}
