import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router';
import { Check, Camera } from 'lucide-react';
import QRCode from 'qrcode';
import jsQR from 'jsqr';
import { getMeetQrByPost, createQrScan, getMeetVerification, ParticipantVerification } from '../../api/meetApi';
import { getMatchDetail } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';

type QrRole = 'author' | 'applicant';

const getQrBaseUrl = () => {
  const configuredUrl = import.meta.env.VITE_QR_BASE_URL;
  return configuredUrl ? configuredUrl.replace(/\/$/, '') : window.location.origin;
};

export default function QRVerificationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const roleParam = searchParams.get('role');

  const [role, setRole] = useState<QrRole | null>(null);
  const [postId, setPostId] = useState<number | null>(null);
  const [step, setStep] = useState<'display' | 'scan' | 'success'>('display');
  const [qrToken, setQrToken] = useState('');
  const [qrImageUrl, setQrImageUrl] = useState(''); // ??異붽?: QR ?대?吏 base64
  const [qrInput, setQrInput] = useState('');
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [scanError, setScanError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const [cameraReady, setCameraReady] = useState(false);
  const [authorNickname, setAuthorNickname] = useState('등록자');
  const [verificationParticipants, setVerificationParticipants] = useState<ParticipantVerification[]>([]);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scanFrameRef = useRef<number | null>(null);
  const scannedTokenRef = useRef('');

  const matchId = Number(id);

  const blockCancelledParticipant = () => {
    alert('매칭 취소자는 인증 페이지에 접근할 수 없습니다.');
    navigate('/matches', { replace: true });
  };

  // ???????????????????????????????????????????
  // ?꾩옱 濡쒓렇???ъ슜??湲곗??쇰줈 ?깅줉???좎껌????븷 ?먮퀎
  // URL??role???놁뼱???щ컮瑜??붾㈃??蹂댁뿬以??
  // roleParam ?좊Т? 愿怨꾩뾾????긽 match detail??fetch??postId瑜???ν븳??
  // ???????????????????????????????????????????
  useEffect(() => {
    const resolveRole = async () => {
      if (!matchId) return;

      const tokenFromUrl = searchParams.get('qrToken');

      try {
        setLoading(true);
        const [matchRes, userRes] = await Promise.all([
          getMatchDetail(matchId),
          getUserMe(),
        ]);

        const match = matchRes.data.data;
        const currentUserId = userRes.data.data.userId;

        setPostId(match.postId);

        if (roleParam === 'author' || roleParam === 'applicant') {
          setRole(roleParam);
          setStep(roleParam === 'applicant' ? 'scan' : 'display');
        } else {
          const resolvedRole = currentUserId === match.authorId ? 'author' : 'applicant';
          setRole(resolvedRole);
          setStep(resolvedRole === 'applicant' || tokenFromUrl ? 'scan' : 'display');
        }
      } catch (err: any) {
        console.error('QR ??븷 ?먮퀎 ?ㅽ뙣:', err);
        const code = err.response?.data?.code;
        if (code === 'MATCH_002' || code === 'CHAT_002' || code === 'CHAT_004') {
          blockCancelledParticipant();
          return;
        }
        alert('留ㅼ묶 ?뺣낫瑜??뺤씤?????놁뒿?덈떎.');
        navigate('/matches');
      } finally {
        setLoading(false);
      }
    };

    resolveRole();
  }, [matchId, navigate, roleParam, searchParams]);

  // ???????????????????????????????????????????
  // ?깅줉?? 留덉슫????QR ?좏겙 諛쒓툒/議고쉶
  // ???????????????????????????????????????????
  useEffect(() => {
    if (role !== 'author' || !postId) return;

    const fetchQr = async () => {
      try {
        setLoading(true);
        const res = await getMeetQrByPost(postId);
        const data = res.data.data; // { postId, qrToken, qrExpiresAt }

        setQrToken(data.qrToken);

        // 留뚮즺 ?쒓컖 湲곗??쇰줈 ?⑥? ?쒓컙(珥? 怨꾩궛
        const expiresAt = new Date(data.qrExpiresAt).getTime();
        const now = new Date().getTime();
        const remainingSeconds = Math.floor((expiresAt - now) / 1000);
        setTimeRemaining(remainingSeconds > 0 ? remainingSeconds : 0);

      } catch (err: any) {
        console.error('QR 諛쒓툒 ?ㅽ뙣:', err.response?.data);
        alert(err.response?.data?.message || 'QR 諛쒓툒???ㅽ뙣?덉뒿?덈떎.');
      } finally {
        setLoading(false);
      }
    };

    fetchQr();
  }, [postId, role]);

  // ???????????????????????????????????????????
  // ??異붽?: qrToken ??QR ?대?吏 ?앹꽦
  // qrToken???명똿?섎뒗 ?쒓컙 QR ?대?吏濡?蹂??
  // ???????????????????????????????????????????
  useEffect(() => {
    // qrToken ?놁쑝硫??ㅽ뻾 ????
    if (!qrToken) return;

    const generateQrImage = async () => {
      try {
        // ?좎껌?먭? ??URL ?앹꽦 (qrToken??荑쇰━ ?뚮씪誘명꽣濡??쎌엯)
        // ??URL??QR濡?留뚮뱾硫??좎껌?먭? ?ㅼ틪 ???먮룞?쇰줈 ?대떦 ?섏씠吏濡??대룞
        const qrUrl = `${getQrBaseUrl()}/matches/${matchId}/qr?role=applicant&qrToken=${encodeURIComponent(qrToken)}`;

        // qrcode 紐⑤뱢濡?URL ??base64 ?대?吏 蹂??
        // toDataURL: canvas??QR 洹몃━怨?PNG base64 臾몄옄?대줈 諛섑솚
        const imageUrl = await QRCode.toDataURL(qrUrl, {
          width: 256,        // QR ?대?吏 ?ш린 (?쎌?)
          margin: 2,         // QR ?щ갚
          color: {
            dark: '#212121',  // QR 肄붾뱶 ?됱긽 (?대몢??遺遺?
            light: '#ffffff', // 諛곌꼍 ?됱긽
          },
        });

        setQrImageUrl(imageUrl);
      } catch (err) {
        console.error('QR ?대?吏 ?앹꽦 ?ㅽ뙣:', err);
      }
    };

    generateQrImage();
  }, [qrToken, matchId]); // qrToken 諛붾??뚮쭏???ъ깮??

  // ???????????????????????????????????????????
  // ??대㉧: 1珥덈쭏???⑥? ?쒓컙 媛먯냼
  // ???????????????????????????????????????????
  useEffect(() => {
    if (timeRemaining <= 0) return;

    const timer = setInterval(() => {
      setTimeRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [timeRemaining]);

  // ???????????????????????????????????????????
  // ?깅줉?먯슜 ?몄쬆 ?꾨즺 polling - 1珥덈쭏???곹깭 議고쉶
  // ?좎껌?먭? QR ?ㅼ틪 ?꾨즺?섎㈃ ?깅줉???붾㈃???먮룞?쇰줈 success濡??꾪솚
  // ???????????????????????????????????????????
  useEffect(() => {
    if (role !== 'author' || !qrToken) return;

    const intervalId = setInterval(async () => {
      try {
        const res = await getMeetVerification(matchId);
        const data = res.data.data;
        setAuthorNickname(data.authorNickname || '등록자');
        setVerificationParticipants(data.participants || []);

        if (data.verificationStatus === 'DONE') {
          setStep('success');
          clearInterval(intervalId);
          setTimeout(() => navigate('/matches'), 2000);
        }
      } catch (err) {
        console.error('?몄쬆 ?곹깭 議고쉶 ?ㅽ뙣:', err);
      }
    }, 1000);

    return () => clearInterval(intervalId);
  }, [matchId, role, qrToken]);

  // ?쒓컙 ?щ㎎ 蹂??(珥???MM:SS)
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // ???????????????????????????????????????????
  // ?좎껌?? QR ?좏겙 ?ㅼ틪 (吏곸젒 ?낅젰 or URL ?먮룞 異붿텧)
  // ???????????????????????????????????????????
  const handleScan = async (tokenOverride?: string) => {
    const tokenToScan = tokenOverride ?? qrInput;
    if (!tokenToScan.trim()) {
      setScanError('QR ?좏겙???낅젰?댁＜?몄슂.');
      return;
    }

    try {
      setLoading(true);
      setScanError('');
      await createQrScan(matchId, tokenToScan.trim());

      setStep('success');
      setTimeout(() => navigate('/matches'), 2000);

    } catch (err: any) {
      setScanError(err.response?.data?.message || 'QR ?몄쬆???ㅽ뙣?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  const stopCamera = () => {
    if (scanFrameRef.current !== null) {
      cancelAnimationFrame(scanFrameRef.current);
      scanFrameRef.current = null;
    }

    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraReady(false);
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
          setCameraError('??釉뚮씪?곗??먯꽌??移대찓?쇰? ?ъ슜?????놁뒿?덈떎.');
          return;
        }

        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        });

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

          try {
            if (videoRef.current.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
              const video = videoRef.current;
              const canvas = canvasRef.current ?? document.createElement('canvas');
              canvasRef.current = canvas;

              const width = video.videoWidth;
              const height = video.videoHeight;

              if (width > 0 && height > 0) {
                canvas.width = width;
                canvas.height = height;

                const context = canvas.getContext('2d', { willReadFrequently: true });
                context?.drawImage(video, 0, 0, width, height);

                const imageData = context?.getImageData(0, 0, width, height);
                const qrCode = imageData ? jsQR(imageData.data, imageData.width, imageData.height) : null;
                const scannedValue = qrCode?.data;

                if (scannedValue) {
                  const token = extractQrToken(scannedValue);
                  if (token && scannedTokenRef.current !== token) {
                    scannedTokenRef.current = token;
                    setQrInput(token);
                    stopCamera();
                    await handleScan(token);
                    return;
                  }
                }
              }
            }
          } catch (err) {
            console.error('QR scan frame failed', err);
          }

          scanFrameRef.current = requestAnimationFrame(scanFrame);
        };

        scanFrameRef.current = requestAnimationFrame(scanFrame);
      } catch (err) {
        console.error('Camera start failed', err);
        setCameraError('移대찓?쇰? ?????놁뒿?덈떎. 釉뚮씪?곗? 移대찓??沅뚰븳???덉슜?댁＜?몄슂.');
      }
    };

    startCameraScanner();

    return () => {
      stopped = true;
      stopCamera();
    };
  }, [role, step, matchId]);

  // ???????????????????????????????????????????
  // ?좎껌?? QR URL濡?吏꾩엯?섎㈃ ?좏겙 異붿텧 ??諛붾줈 ?몄쬆 ?붿껌
  // ???????????????????????????????????????????
  useEffect(() => {
    if (role !== 'applicant') return;

    const tokenFromUrl = searchParams.get('qrToken');
    if (tokenFromUrl) {
      if (scannedTokenRef.current === tokenFromUrl) return;
      scannedTokenRef.current = tokenFromUrl;
      setQrInput(tokenFromUrl);
      setStep('scan');
      handleScan(tokenFromUrl);
    }
  }, [role, searchParams]);

  if (!role) {
    return (
        <div className="max-w-2xl mx-auto">
          <div className="bg-white rounded-2xl shadow-lg p-8 text-center text-[#9e9e9e]">
            QR ?몄쬆 ?뺣낫瑜??뺤씤?섎뒗 以?..
          </div>
        </div>
    );
  }

  // ???????????????????????????????????????????
  // ?좎껌???붾㈃
  // ???????????????????????????????????????????
  if (role === 'applicant') {
    return (
        <div className="max-w-2xl mx-auto">
          <div className="bg-white rounded-2xl shadow-lg p-8">

            {/* ?湲??붾㈃ */}
            {step === 'display' && (
                <div className="text-center">
                  <p className="text-[#616161] mb-6">?곷?諛⑹씠 QR 肄붾뱶瑜??쒖떆???뚭퉴吏 湲곕떎?ㅼ＜?몄슂...</p>
                  <button
                      onClick={() => setStep('scan')}
                      className="px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors"
                  >
                    QR ?ㅼ틪?섍린
                  </button>
                </div>
            )}

            {/* QR ?ㅼ틪 ?낅젰 ?붾㈃ */}
            {step === 'scan' && (
                <>
                  <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 肄붾뱶 ?ㅼ틪</h1>
                  <p className="text-[#616161] text-center mb-8">
                    ?곷?諛⑹씠 蹂댁뿬二쇰뒗 QR 肄붾뱶瑜??ㅼ틪?섏꽭??
                  </p>

                  <div className="relative mb-6 overflow-hidden rounded-2xl border-2 border-dashed border-[#e0e0e0] bg-[#111111]">
                    <video
                        ref={videoRef}
                        autoPlay
                        muted
                        playsInline
                        className="aspect-square w-full object-cover"
                    />
                    {!cameraReady && !cameraError && (
                        <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#fafafa] text-center">
                          <Camera size={64} className="mx-auto mb-4 text-[#bdbdbd]" />
                          <p className="text-[#9e9e9e]">移대찓?쇰? ?щ뒗 以?..</p>
                        </div>
                    )}
                    {cameraReady && (
                        <div className="pointer-events-none absolute inset-8 rounded-2xl border-4 border-white/80 shadow-[0_0_0_999px_rgba(0,0,0,0.25)]" />
                    )}
                    {cameraError && (
                        <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#fafafa] p-8 text-center">
                          <Camera size={64} className="mx-auto mb-4 text-[#bdbdbd]" />
                          <p className="text-sm font-semibold text-[#757575]">{cameraError}</p>
                        </div>
                    )}
                  </div>

                  {/* QR ?좏겙 吏곸젒 ?낅젰 */}
                  <div className="text-center mb-6">
                    <p className="text-sm text-[#9e9e9e] mb-3">?좏겙 吏곸젒 ?낅젰</p>
                    <div className="flex gap-2 max-w-md mx-auto">
                      <input
                          type="text"
                          value={qrInput}
                          onChange={(e) => setQrInput(e.target.value)}
                          placeholder="hp_qr_..."
                          className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                      />
                      <button
                          onClick={() => handleScan()}
                          disabled={loading}
                          className="px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors disabled:opacity-50"
                      >
                        {loading ? '?뺤씤 以?..' : '?뺤씤'}
                      </button>
                    </div>
                  </div>

                  {scanError && (
                      <div className="bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 text-center">
                        <span className="text-[#c62828] text-sm">?좑툘 {scanError}</span>
                      </div>
                  )}
                </>
            )}

            {/* ?몄쬆 ?꾨즺 ?붾㈃ */}
            {step === 'success' && (
                <div className="text-center py-12">
                  <div className="w-20 h-20 bg-[#4caf50] rounded-full flex items-center justify-center mx-auto mb-6">
                    <Check size={48} className="text-white" />
                  </div>
                  <h2 className="text-2xl font-bold text-[#212121] mb-3">???몄쬆 ?꾨즺!</h2>
                  <p className="text-[#616161] mb-4">?묒륫 ?몄쬆??紐⑤몢 ?꾨즺?섏뿀?듬땲??</p>
                  <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-lg px-4 py-3 inline-block">
                    <span className="text-[#2e7d32] text-sm font-semibold">?ъ씤??諛섑솚 ?꾨즺</span>
                  </div>
                </div>
            )}
          </div>
        </div>
    );
  }

  // ???????????????????????????????????????????
  // ?깅줉???붾㈃
  // ???????????????????????????????????????????
  return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-2xl shadow-lg p-8">
          <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 肄붾뱶 ?쒖떆?섍린</h1>
          <p className="text-[#616161] text-center mb-8">
            ?곷?諛⑹뿉寃?QR 肄붾뱶瑜?蹂댁뿬二쇱꽭??<br />
            ?곷?諛⑹쓽 ?ㅼ틪??留뚮궓???몄쬆?⑸땲??
          </p>

          {loading ? (
              <div className="text-center py-12 text-[#9e9e9e]">QR ?좏겙 諛쒓툒 以?..</div>
          ) : (
              <>
                {/* ??蹂寃? ?띿뒪?????QR ?대?吏 ?쒖떆 */}
                <div className="flex justify-center mb-6">
                  {qrImageUrl ? (
                      // QR ?대?吏 ?앹꽦 ?꾨즺 ???대?吏 ?쒖떆
                      <img
                          src={qrImageUrl}
                          alt="留뚮궓 ?몄쬆 QR 肄붾뱶"
                          className="rounded-xl border border-[#e0e0e0]"
                      />
                  ) : (
                      // QR ?대?吏 ?앹꽦 以?
                      <div className="w-64 h-64 bg-[#f5f5f5] rounded-xl flex items-center justify-center">
                        <p className="text-[#9e9e9e] text-sm">QR ?앹꽦 以?..</p>
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
                    {verificationParticipants.length > 0 ? verificationParticipants.map((participant) => (
                      <div key={participant.matchId} className="flex items-center justify-between rounded-lg bg-white px-3 py-2">
                        <div>
                          <p className="text-sm font-semibold text-[#212121]">{participant.nickname || '알 수 없음'}</p>
                          <p className="text-xs text-[#9e9e9e]">신청자</p>
                        </div>
                        <span className={`rounded-full px-3 py-1 text-xs font-bold ${participant.verified ? 'bg-[#e8f5e9] text-[#2e7d32]' : 'bg-[#f5f5f5] text-[#757575]'}`}>
                          {participant.verified ? '스캔 완료' : '미인증'}
                        </span>
                      </div>
                    )) : (
                      <div className="rounded-lg border border-dashed border-[#e0e0e0] bg-white p-3 text-center text-sm text-[#9e9e9e]">
                        아직 참여자 정보가 없습니다.
                      </div>
                    )}
                  </div>
                </div>
                {/* ?⑥? ?좏슚?쒓컙 */}
                <div className="text-center mb-6">
                  <p className="text-sm text-[#9e9e9e] mb-2">?좏슚?쒓컙</p>
                  <p className={`text-4xl font-bold ${timeRemaining < 60 ? 'text-[#ef5350]' : 'text-[#d84315]'}`}>
                    {formatTime(timeRemaining)}
                  </p>
                </div>
              </>
          )}

          {/* success ?붾㈃ */}
          {step === 'success' && (
              <div className="text-center py-8">
                <div className="w-20 h-20 bg-[#4caf50] rounded-full flex items-center justify-center mx-auto mb-6">
                  <Check size={48} className="text-white" />
                </div>
                <h2 className="text-2xl font-bold text-[#212121] mb-3">???몄쬆 ?꾨즺!</h2>
                <p className="text-[#616161]">留뚮궓???뺤씤?섏뿀?듬땲?? ?ъ씤?멸? 諛섑솚?⑸땲??</p>
              </div>
          )}

          <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg p-4">
            <p className="text-sm text-[#ef6c00]">
              ???좎껌?먭? QR???ㅼ틪?섎㈃ 留뚮궓 ?몄쬆???꾨즺?⑸땲??
            </p>
          </div>
        </div>
      </div>
  );
}
