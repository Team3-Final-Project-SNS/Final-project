import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router';
import { Check, Camera } from 'lucide-react';
import QRCode from 'qrcode';
import { getMeetQr, createQrScan, getMeetVerification } from '../../api/meetApi';
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
  const [step, setStep] = useState<'display' | 'scan' | 'success'>('display');
  const [qrToken, setQrToken] = useState('');
  const [qrImageUrl, setQrImageUrl] = useState(''); // ✅ 추가: QR 이미지 base64
  const [qrInput, setQrInput] = useState('');
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [scanError, setScanError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const [cameraReady, setCameraReady] = useState(false);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scanFrameRef = useRef<number | null>(null);
  const scannedTokenRef = useRef('');

  const matchId = Number(id);

  // ───────────────────────────────────────────
  // 현재 로그인 사용자 기준으로 등록자/신청자 역할 판별
  // URL에 role이 없어도 올바른 화면을 보여준다.
  // ───────────────────────────────────────────
  useEffect(() => {
    const resolveRole = async () => {
      if (!matchId) return;

      const tokenFromUrl = searchParams.get('qrToken');
      if (roleParam === 'author' || roleParam === 'applicant') {
        setRole(roleParam);
        setStep(roleParam === 'applicant' ? 'scan' : 'display');
        return;
      }

      try {
        setLoading(true);
        const [matchRes, userRes] = await Promise.all([
          getMatchDetail(matchId),
          getUserMe(),
        ]);

        const match = matchRes.data.data;
        const currentUserId = userRes.data.data.userId;
        const resolvedRole = currentUserId === match.authorId ? 'author' : 'applicant';

        setRole(resolvedRole);
        setStep(resolvedRole === 'applicant' || tokenFromUrl ? 'scan' : 'display');
      } catch (err) {
        console.error('QR 역할 판별 실패:', err);
        alert('매칭 정보를 확인할 수 없습니다.');
        navigate('/matches');
      } finally {
        setLoading(false);
      }
    };

    resolveRole();
  }, [matchId, navigate, roleParam, searchParams]);

  // ───────────────────────────────────────────
  // 등록자: 마운트 시 QR 토큰 발급/조회
  // ───────────────────────────────────────────
  useEffect(() => {
    if (role !== 'author') return;

    const fetchQr = async () => {
      try {
        setLoading(true);
        const res = await getMeetQr(matchId);
        const data = res.data.data; // { matchId, qrToken, expiresAt }

        setQrToken(data.qrToken);

        // 만료 시각 기준으로 남은 시간(초) 계산
        const expiresAt = new Date(data.expiresAt).getTime();
        const now = new Date().getTime();
        const remainingSeconds = Math.floor((expiresAt - now) / 1000);
        setTimeRemaining(remainingSeconds > 0 ? remainingSeconds : 0);

      } catch (err: any) {
        console.error('QR 발급 실패:', err.response?.data);
        alert(err.response?.data?.message || 'QR 발급에 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchQr();
  }, [matchId, role]);

  // ───────────────────────────────────────────
  // ✅ 추가: qrToken → QR 이미지 생성
  // qrToken이 세팅되는 순간 QR 이미지로 변환
  // ───────────────────────────────────────────
  useEffect(() => {
    // qrToken 없으면 실행 안 함
    if (!qrToken) return;

    const generateQrImage = async () => {
      try {
        // 신청자가 열 URL 생성 (qrToken을 쿼리 파라미터로 삽입)
        // 이 URL을 QR로 만들면 신청자가 스캔 시 자동으로 해당 페이지로 이동
        const qrUrl = `${getQrBaseUrl()}/matches/${matchId}/qr?role=applicant&qrToken=${encodeURIComponent(qrToken)}`;

        // qrcode 모듈로 URL → base64 이미지 변환
        // toDataURL: canvas에 QR 그리고 PNG base64 문자열로 반환
        const imageUrl = await QRCode.toDataURL(qrUrl, {
          width: 256,        // QR 이미지 크기 (픽셀)
          margin: 2,         // QR 여백
          color: {
            dark: '#212121',  // QR 코드 색상 (어두운 부분)
            light: '#ffffff', // 배경 색상
          },
        });

        setQrImageUrl(imageUrl);
      } catch (err) {
        console.error('QR 이미지 생성 실패:', err);
      }
    };

    generateQrImage();
  }, [qrToken, matchId]); // qrToken 바뀔 때마다 재생성

  // ───────────────────────────────────────────
  // 타이머: 1초마다 남은 시간 감소
  // ───────────────────────────────────────────
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

  // ───────────────────────────────────────────
  // 등록자용 인증 완료 polling - 1초마다 상태 조회
  // 신청자가 QR 스캔 완료하면 등록자 화면도 자동으로 success로 전환
  // ───────────────────────────────────────────
  useEffect(() => {
    if (role !== 'author' || !qrToken) return;

    const intervalId = setInterval(async () => {
      try {
        const res = await getMeetVerification(matchId);
        const data = res.data.data;

        if (data.verificationStatus === 'DONE') {
          setStep('success');
          clearInterval(intervalId);
          setTimeout(() => navigate('/matches'), 2000);
        }
      } catch (err) {
        console.error('인증 상태 조회 실패:', err);
      }
    }, 1000);

    return () => clearInterval(intervalId);
  }, [matchId, role, qrToken]);

  // 시간 포맷 변환 (초 → MM:SS)
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // ───────────────────────────────────────────
  // 신청자: QR 토큰 스캔 (직접 입력 or URL 자동 추출)
  // ───────────────────────────────────────────
  const handleScan = async (tokenOverride?: string) => {
    const tokenToScan = tokenOverride ?? qrInput;
    if (!tokenToScan.trim()) {
      setScanError('QR 토큰을 입력해주세요.');
      return;
    }

    try {
      setLoading(true);
      setScanError('');
      await createQrScan(matchId, tokenToScan.trim());

      setStep('success');
      setTimeout(() => navigate('/matches'), 2000);

    } catch (err: any) {
      setScanError(err.response?.data?.message || 'QR 인증에 실패했습니다.');
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
          setCameraError('이 브라우저에서는 카메라를 사용할 수 없습니다.');
          return;
        }

        const BarcodeDetectorConstructor = (window as any).BarcodeDetector;
        if (!BarcodeDetectorConstructor) {
          setCameraError('이 브라우저는 실시간 QR 스캔을 지원하지 않습니다. QR을 스캔한 URL로 접속하거나 토큰을 직접 입력해주세요.');
          return;
        }

        const detector = new BarcodeDetectorConstructor({ formats: ['qr_code'] });
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
              const barcodes = await detector.detect(videoRef.current);
              const scannedValue = barcodes?.[0]?.rawValue;

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
          } catch (err) {
            console.error('QR scan frame failed', err);
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
  }, [role, step, matchId]);

  // ───────────────────────────────────────────
  // 신청자: QR URL로 진입하면 토큰 추출 후 바로 인증 요청
  // ───────────────────────────────────────────
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
            QR 인증 정보를 확인하는 중...
          </div>
        </div>
    );
  }

  // ───────────────────────────────────────────
  // 신청자 화면
  // ───────────────────────────────────────────
  if (role === 'applicant') {
    return (
        <div className="max-w-2xl mx-auto">
          <div className="bg-white rounded-2xl shadow-lg p-8">

            {/* 대기 화면 */}
            {step === 'display' && (
                <div className="text-center">
                  <p className="text-[#616161] mb-6">상대방이 QR 코드를 표시할 때까지 기다려주세요...</p>
                  <button
                      onClick={() => setStep('scan')}
                      className="px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors"
                  >
                    QR 스캔하기
                  </button>
                </div>
            )}

            {/* QR 스캔 입력 화면 */}
            {step === 'scan' && (
                <>
                  <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 코드 스캔</h1>
                  <p className="text-[#616161] text-center mb-8">
                    상대방이 보여주는 QR 코드를 스캔하세요.
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
                          <p className="text-[#9e9e9e]">카메라를 여는 중...</p>
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

                  {/* QR 토큰 직접 입력 */}
                  <div className="text-center mb-6">
                    <p className="text-sm text-[#9e9e9e] mb-3">토큰 직접 입력</p>
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
                        {loading ? '확인 중...' : '확인'}
                      </button>
                    </div>
                  </div>

                  {scanError && (
                      <div className="bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 text-center">
                        <span className="text-[#c62828] text-sm">⚠️ {scanError}</span>
                      </div>
                  )}
                </>
            )}

            {/* 인증 완료 화면 */}
            {step === 'success' && (
                <div className="text-center py-12">
                  <div className="w-20 h-20 bg-[#4caf50] rounded-full flex items-center justify-center mx-auto mb-6">
                    <Check size={48} className="text-white" />
                  </div>
                  <h2 className="text-2xl font-bold text-[#212121] mb-3">✅ 인증 완료!</h2>
                  <p className="text-[#616161] mb-4">양측 인증이 모두 완료되었습니다.</p>
                  <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-lg px-4 py-3 inline-block">
                    <span className="text-[#2e7d32] text-sm font-semibold">포인트 반환 완료</span>
                  </div>
                </div>
            )}
          </div>
        </div>
    );
  }

  // ───────────────────────────────────────────
  // 등록자 화면
  // ───────────────────────────────────────────
  return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-2xl shadow-lg p-8">
          <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 코드 제시하기</h1>
          <p className="text-[#616161] text-center mb-8">
            상대방에게 QR 코드를 보여주세요.<br />
            상대방의 스캔이 만남을 인증합니다.
          </p>

          {loading ? (
              <div className="text-center py-12 text-[#9e9e9e]">QR 토큰 발급 중...</div>
          ) : (
              <>
                {/* ✅ 변경: 텍스트 대신 QR 이미지 표시 */}
                <div className="flex justify-center mb-6">
                  {qrImageUrl ? (
                      // QR 이미지 생성 완료 → 이미지 표시
                      <img
                          src={qrImageUrl}
                          alt="만남 인증 QR 코드"
                          className="rounded-xl border border-[#e0e0e0]"
                      />
                  ) : (
                      // QR 이미지 생성 중
                      <div className="w-64 h-64 bg-[#f5f5f5] rounded-xl flex items-center justify-center">
                        <p className="text-[#9e9e9e] text-sm">QR 생성 중...</p>
                      </div>
                  )}
                </div>

                {/* 남은 유효시간 */}
                <div className="text-center mb-6">
                  <p className="text-sm text-[#9e9e9e] mb-2">유효시간</p>
                  <p className={`text-4xl font-bold ${timeRemaining < 60 ? 'text-[#ef5350]' : 'text-[#d84315]'}`}>
                    {formatTime(timeRemaining)}
                  </p>
                </div>
              </>
          )}

          {/* success 화면 */}
          {step === 'success' && (
              <div className="text-center py-8">
                <div className="w-20 h-20 bg-[#4caf50] rounded-full flex items-center justify-center mx-auto mb-6">
                  <Check size={48} className="text-white" />
                </div>
                <h2 className="text-2xl font-bold text-[#212121] mb-3">✅ 인증 완료!</h2>
                <p className="text-[#616161]">만남이 확인되었습니다. 포인트가 반환됩니다.</p>
              </div>
          )}

          <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg p-4">
            <p className="text-sm text-[#ef6c00]">
              ⏰ 신청자가 QR을 스캔하면 만남 인증이 완료됩니다.
            </p>
          </div>
        </div>
      </div>
  );
}
