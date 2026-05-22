import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router';
import { Check, AlertCircle } from 'lucide-react';
import { sendEmailOtp, verifyEmailOtp, signup } from '../../api/authApi';
import { getUniversities, UniversityResponse } from '../../api/univApi';

type SignupStep = 'email' | 'info' | 'complete';

const TERM_ITEMS = [
  { termVersion: 'v1.0-service', label: '서비스 이용약관 동의', required: true },
  { termVersion: 'v1.0-privacy', label: '개인정보 처리방침 동의', required: true },
  { termVersion: 'v1.0-location', label: '위치기반 서비스 이용약관 동의', required: true },
  { termVersion: 'v1.0-marketing', label: '마케팅 정보 수신 동의', required: true },
];

export default function SignupPage() {
  const navigate = useNavigate();

  // ── 단계 관리 ──────────────────────────────────
  const [step, setStep] = useState<SignupStep>('email');

  // ── 공통 상태 ──────────────────────────────────
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // ── Step 1: 이메일 인증 ─────────────────────────
  const [universities, setUniversities] = useState<UniversityResponse[]>([]);
  const [selectedUnivId, setSelectedUnivId] = useState<number | ''>('');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpTimer, setOtpTimer] = useState(0);
  const [universityInfo, setUniversityInfo] = useState<{id: number, name: string} | null>(null);

  // ── Step 2: 정보 입력 ───────────────────────────
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [gender, setGender] = useState<'MALE' | 'FEMALE'>('MALE');
  const [major, setMajor] = useState('');
  const [studentNumber, setStudentNumber] = useState('');

  const [termAgreements, setTermAgreements] = useState(
      TERM_ITEMS.map(({ termVersion }) => ({ termVersion, agreed: false }))
  );

  // ── 초기화 ────────────────────────────────────
  useEffect(() => {
    const fetchUniversities = async () => {
      try {
        const res = await getUniversities();
        setUniversities(res.data.data);
      } catch (err) {
        console.error('대학 목록 조회 실패', err);
        setError('학교 목록을 불러오는데 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    };
    fetchUniversities();
  }, []);

  useEffect(() => {
    let interval: number;
    if (otpSent && otpTimer > 0) {
      interval = window.setInterval(() => {
        setOtpTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [otpSent, otpTimer]);

  const handleTermChange = (version: string, agreed: boolean) => {
    setTermAgreements(prev => prev.map(t => t.termVersion === version ? { ...t, agreed } : t));
  };

  const handleAllTermsChange = (agreed: boolean) => {
    setTermAgreements(prev => prev.map(term => ({ ...term, agreed })));
  };

  const hasAgreed = (version: string) =>
      termAgreements.find(term => term.termVersion === version)?.agreed ?? false;

  const requiredTermsAgreed = TERM_ITEMS
      .filter(term => term.required)
      .every(term => hasAgreed(term.termVersion));

  const allTermsAgreed = TERM_ITEMS.every(term => hasAgreed(term.termVersion));

  const handleSendOTP = async () => {
    const selectedUniversity = universities.find((univ) => univ.universityId === selectedUnivId);

    if (!selectedUniversity) {
      setError('학교를 선택해주세요.');
      return;
    }
    if (!email) {
      setError('이메일을 입력해주세요.');
      return;
    }
    if (!email.endsWith(`@${selectedUniversity.eDomain}`)) {
      setError(`${selectedUniversity.universityName} 이메일은 @${selectedUniversity.eDomain} 형식이어야 합니다.`);
      return;
    }
    setError('');
    setLoading(true);
    try {
      await sendEmailOtp(email);
      setOtpSent(true);
      setOtpTimer(300);
    } catch (err: any) {
      setError(err.response?.data?.message || 'OTP 발송에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOTP = async () => {
    if (!otp) {
      setError('인증 코드를 입력해주세요.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const res = await verifyEmailOtp(email, otp);
      setUniversityInfo({
          id: res.data.data.universityId,
          name: res.data.data.universityName
      });
      setStep('info');
    } catch (err: any) {
      setError(err.response?.data?.message || '인증 코드가 올바르지 않습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async () => {
    if (!name || !nickname || !password || !birthDate || !major || !studentNumber) {
      setError('모든 정보를 입력해주세요.');
      return;
    }
    if (!requiredTermsAgreed) {
      setError('필수 약관에 동의해주세요.');
      return;
    }

    setError('');
    setLoading(true);
    try {
      await signup({
        password,
        name,
        nickname,
        birthDate,
        gender,
        major,
        studentNumber,
        termAgreements
      });
      setStep('complete');
      setTimeout(() => navigate('/login'), 2000);
    } catch (err: any) {
      setError(err.response?.data?.message || '회원가입에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1] relative flex flex-col items-center justify-center p-4">
      {/* ── 상단 로고 (화면 좌측 상단 고정, z-index를 높여서 다른 요소에 가려지지 않게 함) ── */}
      <div className="fixed top-8 left-8 z-[100]">
        <Link
            to="/"
            className="flex items-center gap-2 group p-2 hover:bg-white/80 rounded-xl transition-all shadow-sm border border-[#e0e0e0] bg-white/50 backdrop-blur-sm"
        >
          <span className="text-3xl group-hover:scale-110 transition-transform">🍚</span>
          <span className="text-2xl font-bold text-[#d84315] group-hover:text-[#bf360c]">한끼팟</span>
        </Link>
      </div>

      <div className="w-full max-w-3xl bg-white rounded-lg shadow-sm p-8 relative z-10 mt-16 sm:mt-0">
        
        {/* Step Indicator */}
        <div className="mb-12">
          <div className="flex items-center justify-center gap-4">
            <div className="flex items-center">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${
                  step === 'email' ? 'bg-[#d84315] text-white' : 'bg-[#4caf50] text-white'
              }`}>
                {step === 'email' ? '1' : <Check size={18} />}
              </div>
              <span className="ml-2 text-sm font-medium text-[#424242]">이메일 인증</span>
            </div>
            <div className="w-16 sm:w-24 h-0.5 bg-[#e0e0e0]"></div>
            <div className="flex items-center">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${
                  step === 'email' ? 'bg-[#e0e0e0] text-[#9e9e9e]'
                      : step === 'info' ? 'bg-[#d84315] text-white'
                          : 'bg-[#4caf50] text-white'
              }`}>
                {step === 'complete' ? <Check size={18} /> : '2'}
              </div>
              <span className={`ml-2 text-sm font-medium ${step === 'email' ? 'text-[#9e9e9e]' : 'text-[#424242]'}`}>
                정보 입력
              </span>
            </div>
            <div className="w-16 sm:w-24 h-0.5 bg-[#e0e0e0]"></div>
            <div className="flex items-center">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center ${
                  step === 'complete' ? 'bg-[#d84315] text-white' : 'bg-[#e0e0e0] text-[#9e9e9e]'
              }`}>
                3
              </div>
              <span className={`ml-2 text-sm font-medium ${step === 'complete' ? 'text-[#424242]' : 'text-[#9e9e9e]'}`}>
                가입 완료
              </span>
            </div>
          </div>
        </div>

        {/* Step 1: Email */}
        {step === 'email' && (
            <div>
              <h2 className="text-2xl font-bold text-[#212121] mb-2">학교 이메일 인증</h2>
              <p className="text-[#616161] text-sm mb-8">지원 학교의 이메일 주소를 입력해주세요</p>

              <div className="space-y-6">
                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">학교 선택</label>
                  <select
                      value={selectedUnivId}
                      onChange={(e) => setSelectedUnivId(e.target.value ? Number(e.target.value) : '')}
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent bg-white"
                  >
                    <option value="">학교를 선택하세요</option>
                    {universities.map((univ) => (
                        <option key={univ.universityId} value={univ.universityId}>
                          {univ.universityName} ({univ.eDomain})
                        </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">학교 이메일</label>
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
                        disabled={loading}
                        className="px-6 py-3 bg-[#d84315] text-white rounded-xl font-bold hover:bg-[#bf360c] transition-all shadow-md whitespace-nowrap disabled:bg-[#e0e0e0]"
                    >
                      {loading ? '발송 중...' : 'OTP 발송'}
                    </button>
                  </div>
                </div>

                {error && (
                    <div className="bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
                      <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
                      <span className="text-[#c62828] text-sm">{error}</span>
                    </div>
                )}

                {otpSent && (
                    <div className="space-y-6 pt-4 border-t border-[#f5f5f5]">
                      <div className="bg-[#e8f5e9] border border-[#4caf50] rounded-lg px-4 py-3 flex items-center gap-2">
                        <Check size={18} className="text-[#2e7d32]" />
                        <span className="text-[#2e7d32] text-sm font-medium">인증 코드가 발송되었습니다. 이메일을 확인해주세요.</span>
                      </div>

                      <div>
                        <label className="block text-sm font-medium text-[#424242] mb-2">인증 코드</label>
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
                              disabled={loading}
                              className="px-6 py-3 bg-[#616161] text-white rounded-lg font-semibold hover:bg-[#424242] transition-colors whitespace-nowrap disabled:bg-[#e0e0e0]"
                          >
                            {loading ? '확인 중...' : '확인'}
                          </button>
                        </div>
                        {otpTimer > 0 && (
                            <p className="text-[#d84315] text-sm mt-2 font-medium">유효시간 {formatTime(otpTimer)}</p>
                        )}
                      </div>
                    </div>
                )}
              </div>
            </div>
        )}

        {/* Step 2: Info */}
        {step === 'info' && (
            <div>
              <h2 className="text-2xl font-bold text-[#212121] mb-2">정보 입력</h2>
              <p className="text-[#616161] text-sm mb-8">
                <span className="text-[#d84315] font-bold">{universityInfo?.name}</span> 학생 인증이 완료되었습니다.
              </p>

              {error && (
                  <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
                    <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
                    <span className="text-[#c62828] text-sm">{error}</span>
                  </div>
              )}

              <div className="grid grid-cols-2 gap-x-6 gap-y-4">
                <div className="col-span-2">
                  <h3 className="font-semibold text-[#212121] mb-2">기본 정보</h3>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">이름</label>
                  <input
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="홍길동"
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">닉네임</label>
                  <input
                      type="text"
                      value={nickname}
                      onChange={(e) => setNickname(e.target.value)}
                      placeholder="길동이"
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block text-sm font-medium text-[#424242] mb-2">비밀번호</label>
                  <input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="8~20자 영문, 숫자 조합"
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">생년월일</label>
                  <input
                      type="date"
                      value={birthDate}
                      onChange={(e) => setBirthDate(e.target.value)}
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">성별</label>
                  <div className="flex gap-2">
                    <button
                        type="button"
                        onClick={() => setGender('MALE')}
                        className={`flex-1 py-3 rounded-lg font-semibold transition-all ${
                            gender === 'MALE' ? 'bg-[#d84315] text-white shadow-md' : 'bg-white border border-[#e0e0e0] text-[#616161]'
                        }`}
                    >
                      남성
                    </button>
                    <button
                        type="button"
                        onClick={() => setGender('FEMALE')}
                        className={`flex-1 py-3 rounded-lg font-semibold transition-all ${
                            gender === 'FEMALE' ? 'bg-[#d84315] text-white shadow-md' : 'bg-white border border-[#e0e0e0] text-[#616161]'
                        }`}
                    >
                      여성
                    </button>
                  </div>
                </div>

                <div className="col-span-2 mt-4">
                  <h3 className="font-semibold text-[#212121] mb-2">학교 정보</h3>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">전공</label>
                  <input
                      type="text"
                      value={major}
                      onChange={(e) => setMajor(e.target.value)}
                      placeholder="컴퓨터공학과"
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">학번 (입학년도)</label>
                  <input
                      type="text"
                      value={studentNumber}
                      onChange={(e) => setStudentNumber(e.target.value)}
                      placeholder="예: 24"
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                  />
                </div>

                <div className="col-span-2 mt-6">
                  <h3 className="font-semibold text-[#212121] mb-4">약관 동의</h3>
                  <div className="space-y-3 bg-[#fafafa] p-4 rounded-xl border border-[#f0f0f0]">
                    <label className="flex items-start gap-3 cursor-pointer group border-b border-[#eeeeee] pb-3">
                      <input
                          type="checkbox"
                          checked={allTermsAgreed}
                          onChange={(e) => handleAllTermsChange(e.target.checked)}
                          className="mt-1 w-4 h-4 text-[#d84315] border-[#e0e0e0] rounded focus:ring-[#d84315]"
                      />
                      <span className="text-sm font-semibold text-[#212121] group-hover:text-[#d84315]">전체 동의</span>
                    </label>
                    {TERM_ITEMS.map((term) => (
                        <label key={term.termVersion} className="flex items-start gap-3 cursor-pointer group">
                          <input
                              type="checkbox"
                              checked={hasAgreed(term.termVersion)}
                              onChange={(e) => handleTermChange(term.termVersion, e.target.checked)}
                              className="mt-1 w-4 h-4 text-[#d84315] border-[#e0e0e0] rounded focus:ring-[#d84315]"
                          />
                          <span className="text-sm text-[#424242] group-hover:text-[#212121]">
                            [{term.required ? '필수' : '선택'}] {term.label}
                          </span>
                        </label>
                    ))}
                    <p className="text-[10px] text-[#9e9e9e] mt-2 pl-7">Policy Version: v1.0.0</p>
                  </div>
                </div>
              </div>

              <button
                  onClick={handleSignup}
                  disabled={!requiredTermsAgreed || loading}
                  className="w-full mt-10 bg-[#d84315] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg disabled:bg-[#e0e0e0] disabled:cursor-not-allowed disabled:shadow-none flex items-center justify-center gap-2"
              >
                {loading ? '처리 중...' : '가입 완료 → 10,000P 지급'}
              </button>
            </div>
        )}

        {/* Step 3: Success */}
        {step === 'complete' && (
            <div className="text-center py-16">
              <div className="w-20 h-20 bg-[#e8f5e9] rounded-full flex items-center justify-center mx-auto mb-6 shadow-sm">
                <Check size={40} className="text-[#4caf50]" />
              </div>
              <h2 className="text-3xl font-bold text-[#212121] mb-4">환영합니다! 가입이 완료되었습니다</h2>
              <p className="text-[#616161] mb-2">회원가입 축하 보너스 <span className="text-[#d84315] font-bold">10,000P</span>가 지급되었습니다.</p>
              <p className="text-sm text-[#9e9e9e]">잠시 후 로그인 페이지로 이동합니다...</p>
            </div>
        )}

      </div>
    </div>
  );
}
