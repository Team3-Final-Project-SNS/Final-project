import { useState } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router';
import { Check, Camera } from 'lucide-react';

export default function QRVerificationPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const role = searchParams.get('role') || 'author'; // 'author' or 'applicant'
  const [step, setStep] = useState<'display' | 'scan' | 'success'>('display');
  const [timeRemaining, setTimeRemaining] = useState(272);
  const [scanError, setScanError] = useState('');

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleComplete = () => {
    navigate('/matches');
  };

  const handleScan = () => {
    // Simulate successful scan after a moment
    setTimeout(() => {
      setStep('success');
      setTimeout(() => {
        navigate('/matches');
      }, 2000);
    }, 1500);
  };

  // Applicant view - QR Scan
  if (role === 'applicant') {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-2xl shadow-lg p-8">
          {step === 'scan' && (
            <>
              <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 코드 스캔</h1>
              <p className="text-[#616161] text-center mb-8">
                상대방의 보여주는 QR 코드를 직접 인증하거나, 카메라로 스캔하세요.
              </p>

              <div className="bg-[#fafafa] border-2 border-dashed border-[#e0e0e0] rounded-2xl p-12 mb-6 text-center">
                <Camera size={64} className="text-[#bdbdbd] mx-auto mb-4" />
                <p className="text-[#9e9e9e] mb-4">카메라 스캔 영역</p>
                <button
                  onClick={handleScan}
                  className="px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors"
                >
                  카메라 스캔하기
                </button>
              </div>

              <div className="text-center mb-6">
                <p className="text-sm text-[#9e9e9e] mb-3">또는 코드 직접 입력</p>
                <div className="flex gap-2 max-w-md mx-auto">
                  <input
                    type="text"
                    placeholder="QR 토큰 입력"
                    className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                  <button
                    onClick={handleScan}
                    className="px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors"
                  >
                    확인
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

          {step === 'success' && (
            <div className="text-center py-12">
              <div className="w-20 h-20 bg-[#4caf50] rounded-full flex items-center justify-center mx-auto mb-6">
                <Check size={48} className="text-white" />
              </div>
              <h2 className="text-2xl font-bold text-[#212121] mb-3">✅ 인증 완료!</h2>
              <p className="text-[#616161] mb-4">양측 인증이 모두 완료되었습니다.</p>
              <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-lg px-4 py-3 inline-block">
                <span className="text-[#2e7d32] text-sm font-semibold">+3,000P 반환 완료</span>
              </div>
            </div>
          )}

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
        </div>
      </div>
    );
  }

  // Author view - QR Display
  return (
    <div className="max-w-2xl mx-auto">
      <div className="bg-white rounded-2xl shadow-lg p-8">
        <h1 className="text-2xl font-bold text-[#212121] mb-2 text-center">QR 코드 제시하기</h1>
        <p className="text-[#616161] text-center mb-8">
          상대방에게 QR 코드를 보여주세요.<br />
          상대방의 스캔이 만남을 인증합니다.
        </p>

        <div className="bg-gradient-to-br from-[#fafafa] to-white border-2 border-[#e0e0e0] rounded-2xl p-8 mb-6">
          <div className="flex items-center justify-center mb-6">
            <div className="w-72 h-72 bg-white rounded-xl shadow-inner border-2 border-[#e0e0e0] flex items-center justify-center p-6">
              <svg viewBox="0 0 100 100" className="w-full h-full">
                <rect x="10" y="10" width="15" height="15" fill="black" />
                <rect x="35" y="10" width="15" height="15" fill="black" />
                <rect x="75" y="10" width="15" height="15" fill="black" />
                <rect x="10" y="35" width="15" height="15" fill="black" />
                <rect x="45" y="45" width="10" height="10" fill="black" />
                <rect x="10" y="75" width="15" height="15" fill="black" />
                <rect x="35" y="75" width="15" height="15" fill="black" />
                <rect x="75" y="75" width="15" height="15" fill="black" />
              </svg>
            </div>
          </div>

          <div className="text-center">
            <p className="text-sm text-[#9e9e9e] mb-2">유효시간</p>
            <p className="text-4xl font-bold text-[#d84315]">{formatTime(timeRemaining)}</p>
          </div>
        </div>

        <div className="bg-gradient-to-br from-[#f5f5f5] to-white rounded-xl p-5 mb-6 border border-[#e0e0e0]">
          <h3 className="font-semibold text-[#212121] mb-4 flex items-center gap-2">
            <span className="w-2 h-2 bg-[#4caf50] rounded-full"></span>
            인증 현황
          </h3>

          <div className="space-y-3">
            <div className="flex items-center justify-between bg-white rounded-lg p-3">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 bg-[#4caf50] rounded-full flex items-center justify-center">
                  <Check size={16} className="text-white" />
                </div>
                <span className="text-sm font-medium text-[#212121]">나 (등록자)</span>
              </div>
              <div className="text-right">
                <span className="text-xs text-[#4caf50] font-semibold block">완료</span>
                <span className="text-xs text-[#9e9e9e]">QR 제시 기록됨</span>
              </div>
            </div>

            <div className="flex items-center justify-between bg-white rounded-lg p-3">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 bg-[#e0e0e0] rounded-full flex items-center justify-center">
                  <span className="text-sm">⏱</span>
                </div>
                <span className="text-sm font-medium text-[#212121]">밥먹자 (신청자)</span>
              </div>
              <div className="text-right">
                <span className="text-xs text-[#9e9e9e] font-semibold block">대기</span>
                <span className="text-xs text-[#9e9e9e]">스캔 대기 중</span>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg p-4 mb-6">
          <p className="text-sm text-[#ef6c00]">
            ⏰ 양측 인증 완료 시<br />
            예치된 <strong>3,000P × 2</strong> 즉시 반환됩니다.
          </p>
        </div>

        <button
          onClick={handleComplete}
          className="w-full py-3 bg-[#f5f5f5] text-[#9e9e9e] rounded-lg font-semibold cursor-not-allowed"
          disabled
        >
          매칭 취소
        </button>
      </div>
    </div>
  );
}
