import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Check } from 'lucide-react';

type SignupStep = 'email' | 'info' | 'complete';

export default function SignupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<SignupStep>('email');
  const [email, setEmail] = useState('');
  const [school, setSchool] = useState('○○대학교');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpTimer, setOtpTimer] = useState(0);

  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [gender, setGender] = useState<'male' | 'female'>('male');
  const [major, setMajor] = useState('');
  const [studentId, setStudentId] = useState('');
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);

  const handleSendOTP = () => {
    setOtpSent(true);
    setOtpTimer(300);
  };

  const handleVerifyOTP = () => {
    setStep('info');
  };

  const handleSignup = () => {
    navigate('/login');
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
      <div className="min-h-screen bg-[#fafafa] flex items-center justify-center p-4">
        <div className="w-full max-w-3xl bg-white rounded-lg shadow-sm p-8">
          <div className="mb-8">
            <div className="flex items-center justify-center gap-4 mb-8">
              <div className="flex items-center">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'email' ? 'bg-[#d84315] text-white' : 'bg-[#4caf50] text-white'}`}>
                  {step === 'email' ? '1' : <Check size={18} />}
                </div>
                <span className="ml-2 text-sm font-medium text-[#424242]">이메일 인증</span>
              </div>

              <div className="w-24 h-0.5 bg-[#e0e0e0]"></div>

              <div className="flex items-center">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'email' ? 'bg-[#e0e0e0] text-[#9e9e9e]' : step === 'info' ? 'bg-[#d84315] text-white' : 'bg-[#4caf50] text-white'}`}>
                  {step === 'complete' ? <Check size={18} /> : '2'}
                </div>
                <span className={`ml-2 text-sm font-medium ${step === 'email' ? 'text-[#9e9e9e]' : 'text-[#424242]'}`}>정보 입력</span>
              </div>

              <div className="w-24 h-0.5 bg-[#e0e0e0]"></div>

              <div className="flex items-center">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center ${step === 'complete' ? 'bg-[#d84315] text-white' : 'bg-[#e0e0e0] text-[#9e9e9e]'}`}>
                  3
                </div>
                <span className={`ml-2 text-sm font-medium ${step === 'complete' ? 'text-[#424242]' : 'text-[#9e9e9e]'}`}>가입 완료</span>
              </div>
            </div>
          </div>

          {step === 'email' && (
              <div>
                <h2 className="text-2xl font-bold text-[#212121] mb-2">학교 이메일 인증</h2>
                <p className="text-[#616161] text-sm mb-8">지원 학교의 이메일 주소를 입력해주세요</p>

                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      학교 선택
                    </label>
                    <select
                        value={school}
                        onChange={(e) => setSchool(e.target.value)}
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    >
                      <option>○○대학교 (university.ac.kr)</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      학교 이메일
                    </label>
                    <div className="flex gap-2">
                      <input
                          type="email"
                          value={email}
                          onChange={(e) => setEmail(e.target.value)}
                          placeholder="hong@university.ac.kr"
                          className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                      />
                      <button
                          onClick={handleSendOTP}
                          className="px-6 py-3 bg-[#d84315] text-white rounded-xl font-bold hover:bg-[#bf360c] transition-all shadow-md whitespace-nowrap"
                      >
                        OTP 발송
                      </button>
                    </div>
                  </div>

                  {otpSent && (
                      <>
                        <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-lg px-4 py-3 flex items-start gap-2">
                          <span className="text-[#2e7d32] text-sm">✅ 인증 코드가 발송되었습니다. 이메일을 확인해주세요.</span>
                        </div>

                        <div>
                          <label className="block text-sm font-medium text-[#424242] mb-2">
                            인증 코드
                          </label>
                          <div className="flex gap-2">
                            <input
                                type="text"
                                value={otp}
                                onChange={(e) => setOtp(e.target.value)}
                                placeholder="6자리 코드 입력"
                                className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                            />
                            <button
                                onClick={handleVerifyOTP}
                                className="px-6 py-3 bg-[#616161] text-white rounded-lg font-semibold hover:bg-[#424242] transition-colors whitespace-nowrap"
                            >
                              확인
                            </button>
                          </div>
                          {otpTimer > 0 && (
                              <p className="text-[#d84315] text-sm mt-2">유효시간 {formatTime(otpTimer)}</p>
                          )}
                        </div>
                      </>
                  )}
                </div>
              </div>
          )}

          {step === 'info' && (
              <div>
                <h2 className="text-2xl font-bold text-[#212121] mb-8">정보 입력</h2>

                <div className="grid grid-cols-2 gap-6">
                  <div className="col-span-2">
                    <h3 className="font-semibold text-[#212121] mb-4">기본 정보</h3>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      이름
                    </label>
                    <input
                        type="text"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="홍길동"
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      닉네임
                    </label>
                    <input
                        type="text"
                        value={nickname}
                        onChange={(e) => setNickname(e.target.value)}
                        placeholder="길동이"
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      비밀번호
                    </label>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="••••••••"
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      생년월일
                    </label>
                    <input
                        type="date"
                        value={birthDate}
                        onChange={(e) => setBirthDate(e.target.value)}
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      성별
                    </label>
                    <div className="flex gap-2">
                      <button
                          type="button"
                          onClick={() => setGender('male')}
                          className={`flex-1 py-3 rounded-lg font-semibold transition-colors ${gender === 'male' ? 'bg-[#d84315] text-white' : 'bg-white border border-[#e0e0e0] text-[#616161]'}`}
                      >
                        남성
                      </button>
                      <button
                          type="button"
                          onClick={() => setGender('female')}
                          className={`flex-1 py-3 rounded-lg font-semibold transition-colors ${gender === 'female' ? 'bg-[#d84315] text-white' : 'bg-white border border-[#e0e0e0] text-[#616161]'}`}
                      >
                        여성
                      </button>
                    </div>
                  </div>

                  <div className="col-span-2 mt-4">
                    <h3 className="font-semibold text-[#212121] mb-4">학교 정보</h3>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      전공
                    </label>
                    <input
                        type="text"
                        value={major}
                        onChange={(e) => setMajor(e.target.value)}
                        placeholder="컴퓨터공학과"
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#424242] mb-2">
                      학번
                    </label>
                    <input
                        type="text"
                        value={studentId}
                        onChange={(e) => setStudentId(e.target.value)}
                        placeholder="20230001"
                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    />
                  </div>

                  <div className="col-span-2 mt-4">
                    <h3 className="font-semibold text-[#212121] mb-4">약관 동의</h3>
                    <div className="space-y-3">
                      <label className="flex items-start gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={termsAccepted}
                            onChange={(e) => setTermsAccepted(e.target.checked)}
                            className="mt-1"
                        />
                        <span className="text-sm text-[#424242]">[필수] 서비스 이용약관 동의</span>
                      </label>
                      <label className="flex items-start gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={privacyAccepted}
                            onChange={(e) => setPrivacyAccepted(e.target.checked)}
                            className="mt-1"
                        />
                        <span className="text-sm text-[#424242]">[필수] 개인정보 처리방침 동의</span>
                      </label>
                      <p className="text-xs text-[#9e9e9e] mt-4">작은 약관 비전: v1.0</p>
                    </div>
                  </div>
                </div>

                <button
                    onClick={handleSignup}
                    disabled={!termsAccepted || !privacyAccepted}
                    className="w-full mt-8 bg-[#d84315] text-white py-4 rounded-xl font-bold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg disabled:bg-[#e0e0e0] disabled:cursor-not-allowed disabled:shadow-none"
                >
                  가입 완료 → 10,000P 지급
                </button>
              </div>
          )}
        </div>
      </div>
  );
}
