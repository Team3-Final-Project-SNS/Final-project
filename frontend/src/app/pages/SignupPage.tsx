import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import ReactMarkdown from 'react-markdown';
import { AlertCircle, CalendarIcon, Check, X } from 'lucide-react';
import { sendEmailOtp, signup, verifyEmailOtp } from '../../api/authApi';
import { getUniversities, UniversityResponse } from '../../api/univApi';
import { Calendar } from '../components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '../components/ui/popover';
import termsOfServiceContent from '../../assets/terms/terms-of-service.md?raw';
import privacyPolicyContent from '../../assets/terms/privacy-policy.md?raw';
import locationTermsContent from '../../assets/terms/location-terms.md?raw';
import marketingConsentContent from '../../assets/terms/marketing-consent.md?raw';

type SignupStep = 'email' | 'info' | 'complete';

const TERM_ITEMS = [
  { termVersion: 'v1.0-service', label: '서비스 이용약관 동의', required: true, title: '서비스 이용약관', content: termsOfServiceContent },
  { termVersion: 'v1.0-privacy', label: '개인정보 처리방침 동의', required: true, title: '개인정보 처리방침', content: privacyPolicyContent },
  { termVersion: 'v1.0-location', label: '위치기반 서비스 이용약관 동의', required: true, title: '위치기반 서비스 이용약관', content: locationTermsContent },
  { termVersion: 'v1.0-marketing', label: '마케팅 정보 수신 동의', required: false, title: '마케팅 정보 수신 동의', content: marketingConsentContent },
];

function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatBirthDate(value: string) {
  if (!value) return '생년월일을 선택하세요';
  return new Date(`${value}T00:00:00`).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

export default function SignupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<SignupStep>('email');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [universities, setUniversities] = useState<UniversityResponse[]>([]);
  const [selectedUnivId, setSelectedUnivId] = useState<number | ''>('');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpTimer, setOtpTimer] = useState(0);
  const [universityInfo, setUniversityInfo] = useState<{ id: number; name: string } | null>(null);
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [gender, setGender] = useState<'MALE' | 'FEMALE'>('MALE');
  const [major, setMajor] = useState('');
  const [studentNumber, setStudentNumber] = useState('');
  const [openTerm, setOpenTerm] = useState<(typeof TERM_ITEMS)[number] | null>(null);
  const [termAgreements, setTermAgreements] = useState(
    TERM_ITEMS.map(({ termVersion }) => ({ termVersion, agreed: false })),
  );

  useEffect(() => {
    const fetchUniversities = async () => {
      try {
        const res = await getUniversities();
        setUniversities(res.data.data);
      } catch (err) {
        console.error('대학 목록 조회 실패', err);
        setError('대학 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    };
    fetchUniversities();
  }, []);

  useEffect(() => {
    if (!otpSent || otpTimer <= 0) return;
    const interval = window.setInterval(() => setOtpTimer((prev) => Math.max(prev - 1, 0)), 1000);
    return () => window.clearInterval(interval);
  }, [otpSent, otpTimer]);

  const handleTermChange = (version: string, agreed: boolean) => {
    setTermAgreements((prev) => prev.map((term) => term.termVersion === version ? { ...term, agreed } : term));
  };

  const handleAllTermsChange = (agreed: boolean) => {
    setTermAgreements((prev) => prev.map((term) => ({ ...term, agreed })));
  };

  const hasAgreed = (version: string) =>
    termAgreements.find((term) => term.termVersion === version)?.agreed ?? false;

  const requiredTermsAgreed = TERM_ITEMS
    .filter((term) => term.required)
    .every((term) => hasAgreed(term.termVersion));

  const allTermsAgreed = TERM_ITEMS.every((term) => hasAgreed(term.termVersion));

  const handleSendOTP = async () => {
    const selectedUniversity = universities.find((univ) => univ.universityId === selectedUnivId);

    if (!selectedUniversity) {
      setError('대학을 선택해주세요.');
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
      setError(err.response?.data?.message || '인증번호 요청에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOTP = async () => {
    if (!otp) {
      setError('인증번호를 입력해주세요.');
      return;
    }

    setError('');
    setLoading(true);
    try {
      const res = await verifyEmailOtp(email, otp);
      setUniversityInfo({
        id: res.data.data.universityId,
        name: res.data.data.universityName,
      });
      setStep('info');
    } catch (err: any) {
      setError(err.response?.data?.message || '인증번호가 올바르지 않습니다.');
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
        termAgreements,
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
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1] p-4">
      <div className="fixed left-8 top-8 z-[100]">
        <Link to="/" className="flex items-center gap-2 rounded-xl border border-[#e0e0e0] bg-white/70 p-2 shadow-sm backdrop-blur-sm transition-all hover:bg-white">
          <span className="text-3xl">🍚</span>
          <span className="text-2xl font-bold text-[#d84315]">한끼팟</span>
        </Link>
      </div>

      <div className="relative z-10 mt-16 w-full max-w-3xl rounded-lg bg-white p-8 shadow-sm sm:mt-0">
        <StepIndicator step={step} />

        {step === 'email' && (
          <div>
            <h2 className="mb-2 text-2xl font-bold text-[#212121]">학교 이메일 인증</h2>
            <p className="mb-8 text-sm text-[#616161]">지원 대학의 이메일 주소를 입력해주세요.</p>

            <div className="space-y-6">
              <div>
                <label className="mb-2 block text-sm font-medium text-[#424242]">대학 선택</label>
                <select
                  value={selectedUnivId}
                  onChange={(event) => setSelectedUnivId(event.target.value ? Number(event.target.value) : '')}
                  className="w-full rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                >
                  <option value="">대학을 선택하세요</option>
                  {universities.map((univ) => (
                    <option key={univ.universityId} value={univ.universityId}>
                      {univ.universityName} ({univ.eDomain})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-[#424242]">학교 이메일</label>
                <div className="flex gap-2">
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="hong@university.ac.kr"
                    className="flex-1 rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                  />
                  <button
                    type="button"
                    onClick={handleSendOTP}
                    disabled={loading}
                    className="whitespace-nowrap rounded-xl bg-[#d84315] px-6 py-3 font-bold text-white shadow-md transition-all hover:bg-[#bf360c] disabled:bg-[#e0e0e0]"
                  >
                    {loading ? '요청 중...' : otpSent ? '인증번호 재요청' : '인증번호 요청'}
                  </button>
                </div>
              </div>

              {error && <ErrorNotice message={error} />}

              {otpSent && (
                <div className="space-y-6 border-t border-[#f5f5f5] pt-4">
                  <div className="flex items-center gap-2 rounded-lg border border-[#4caf50] bg-[#e8f5e9] px-4 py-3">
                    <Check size={18} className="text-[#2e7d32]" />
                    <span className="text-sm font-medium text-[#2e7d32]">인증번호가 발송되었습니다. 이메일을 확인해주세요.</span>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-medium text-[#424242]">인증번호</label>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={otp}
                        onChange={(event) => setOtp(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            handleVerifyOTP();
                          }
                        }}
                        placeholder="6자리 인증번호 입력"
                        className="flex-1 rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                      />
                      <button
                        type="button"
                        onClick={handleVerifyOTP}
                        disabled={loading}
                        className="whitespace-nowrap rounded-lg bg-[#616161] px-6 py-3 font-semibold text-white transition-colors hover:bg-[#424242] disabled:bg-[#e0e0e0]"
                      >
                        {loading ? '확인 중...' : '확인'}
                      </button>
                    </div>
                    {otpTimer > 0 && <p className="mt-2 text-sm font-medium text-[#d84315]">유효시간 {formatTime(otpTimer)}</p>}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {step === 'info' && (
          <div>
            <h2 className="mb-2 text-2xl font-bold text-[#212121]">정보 입력</h2>
            <p className="mb-8 text-sm text-[#616161]">
              <span className="font-bold text-[#d84315]">{universityInfo?.name}</span> 학생 인증이 완료되었습니다.
            </p>

            {error && <ErrorNotice message={error} />}

            <div className="grid grid-cols-2 gap-x-6 gap-y-4">
              <SectionTitle>기본 정보</SectionTitle>
              <TextField label="이름" value={name} onChange={setName} placeholder="홍길동" />
              <TextField label="닉네임" value={nickname} onChange={setNickname} placeholder="길동이" />
              <div className="col-span-2">
                <TextField label="비밀번호" type="password" value={password} onChange={setPassword} placeholder="8~20자 영문, 숫자 조합" />
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-[#424242]">생년월일</label>
                <Popover>
                  <PopoverTrigger asChild>
                    <button
                      type="button"
                      className="flex w-full items-center justify-between rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-left transition-colors hover:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                    >
                      <span className={birthDate ? 'font-semibold text-[#212121]' : 'text-[#9e9e9e]'}>{formatBirthDate(birthDate)}</span>
                      <CalendarIcon size={18} className="text-[#d84315]" />
                    </button>
                  </PopoverTrigger>
                  <PopoverContent align="start" className="w-auto rounded-2xl border-[#eeeeee] bg-white p-3 shadow-xl">
                    <Calendar
                      mode="single"
                      selected={birthDate ? new Date(`${birthDate}T00:00:00`) : undefined}
                      onSelect={(date) => date && setBirthDate(toDateInputValue(date))}
                      fromYear={1950}
                      toYear={new Date().getFullYear()}
                      captionLayout="dropdown-buttons"
                      disabled={(date) => date > new Date()}
                      initialFocus
                      className="rounded-xl bg-white"
                      classNames={{
                        caption_dropdowns: 'flex justify-center gap-2',
                        dropdown: 'rounded-lg border border-[#e0e0e0] bg-white px-2 py-1 text-sm font-semibold outline-none',
                        day_selected: 'bg-[#d84315] text-white hover:bg-[#bf360c] focus:bg-[#bf360c]',
                        day_today: 'bg-[#fff3e0] text-[#d84315]',
                      }}
                    />
                  </PopoverContent>
                </Popover>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-[#424242]">성별</label>
                <div className="flex gap-2">
                  <GenderButton active={gender === 'MALE'} onClick={() => setGender('MALE')}>남성</GenderButton>
                  <GenderButton active={gender === 'FEMALE'} onClick={() => setGender('FEMALE')}>여성</GenderButton>
                </div>
              </div>

              <SectionTitle className="mt-4">학교 정보</SectionTitle>
              <TextField label="전공" value={major} onChange={setMajor} placeholder="컴퓨터공학과" />
              <TextField label="학번 (입학연도)" value={studentNumber} onChange={setStudentNumber} placeholder="24" />

              <div className="col-span-2 mt-6">
                <h3 className="mb-4 font-semibold text-[#212121]">약관 동의</h3>
                <div className="space-y-3 rounded-xl border border-[#f0f0f0] bg-[#fafafa] p-4">
                  <label className="group flex cursor-pointer items-start gap-3 border-b border-[#eeeeee] pb-3">
                    <input
                      type="checkbox"
                      checked={allTermsAgreed}
                      onChange={(event) => handleAllTermsChange(event.target.checked)}
                      className="mt-1 h-4 w-4 rounded border-[#e0e0e0] text-[#d84315] focus:ring-[#d84315]"
                    />
                    <span className="text-sm font-semibold text-[#212121] group-hover:text-[#d84315]">전체 동의</span>
                  </label>

                  {TERM_ITEMS.map((term) => (
                    <div key={term.termVersion} className="flex items-start justify-between gap-3">
                      <label className="group flex min-w-0 flex-1 cursor-pointer items-start gap-3">
                        <input
                          type="checkbox"
                          checked={hasAgreed(term.termVersion)}
                          onChange={(event) => handleTermChange(term.termVersion, event.target.checked)}
                          className="mt-1 h-4 w-4 rounded border-[#e0e0e0] text-[#d84315] focus:ring-[#d84315]"
                        />
                        <span className="text-sm text-[#424242] group-hover:text-[#212121]">
                          [{term.required ? '필수' : '선택'}] {term.label}
                        </span>
                      </label>
                      <button
                        type="button"
                        onClick={() => setOpenTerm(term)}
                        className="shrink-0 text-xs font-bold text-[#d84315] underline-offset-2 hover:underline"
                      >
                        자세히 보기
                      </button>
                    </div>
                  ))}
                  <p className="mt-2 pl-7 text-[10px] text-[#9e9e9e]">Policy Version: v1.0.0</p>
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={handleSignup}
              disabled={!requiredTermsAgreed || loading}
              className="mt-10 flex w-full items-center justify-center gap-2 rounded-xl bg-[#d84315] py-4 text-lg font-bold text-white shadow-md transition-all hover:bg-[#bf360c] hover:shadow-lg disabled:cursor-not-allowed disabled:bg-[#e0e0e0] disabled:shadow-none"
            >
              {loading ? '처리 중...' : '가입 완료 + 10,000P 지급'}
            </button>
          </div>
        )}

        {step === 'complete' && (
          <div className="py-16 text-center">
            <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#e8f5e9] shadow-sm">
              <Check size={40} className="text-[#4caf50]" />
            </div>
            <h2 className="mb-4 text-3xl font-bold text-[#212121]">환영합니다! 가입이 완료되었습니다.</h2>
            <p className="mb-2 text-[#616161]">회원가입 축하 보너스 <span className="font-bold text-[#d84315]">10,000P</span>가 지급되었습니다.</p>
            <p className="text-sm text-[#9e9e9e]">잠시 후 로그인 페이지로 이동합니다.</p>
          </div>
        )}
      </div>

      {openTerm && <TermModal term={openTerm} onClose={() => setOpenTerm(null)} />}
    </div>
  );
}

function StepIndicator({ step }: { step: SignupStep }) {
  return (
    <div className="mb-12 flex items-center justify-center gap-4">
      <StepDot active={step === 'email'} complete={step !== 'email'} label="이메일 인증" number="1" />
      <div className="h-0.5 w-16 bg-[#e0e0e0] sm:w-24" />
      <StepDot active={step === 'info'} complete={step === 'complete'} label="정보 입력" number="2" muted={step === 'email'} />
      <div className="h-0.5 w-16 bg-[#e0e0e0] sm:w-24" />
      <StepDot active={step === 'complete'} complete={false} label="가입 완료" number="3" muted={step !== 'complete'} />
    </div>
  );
}

function StepDot({ active, complete, muted, label, number }: { active: boolean; complete: boolean; muted?: boolean; label: string; number: string }) {
  return (
    <div className="flex items-center">
      <div className={`flex h-8 w-8 items-center justify-center rounded-full ${active ? 'bg-[#d84315] text-white' : complete ? 'bg-[#4caf50] text-white' : 'bg-[#e0e0e0] text-[#9e9e9e]'}`}>
        {complete ? <Check size={18} /> : number}
      </div>
      <span className={`ml-2 text-sm font-medium ${muted ? 'text-[#9e9e9e]' : 'text-[#424242]'}`}>{label}</span>
    </div>
  );
}

function ErrorNotice({ message }: { message: string }) {
  return (
    <div className="mb-6 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3">
      <AlertCircle size={18} className="mt-0.5 text-[#c62828]" />
      <span className="text-sm text-[#c62828]">{message}</span>
    </div>
  );
}

function SectionTitle({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`col-span-2 ${className}`}><h3 className="mb-2 font-semibold text-[#212121]">{children}</h3></div>;
}

function TextField({ label, type = 'text', value, onChange, placeholder }: { label: string; type?: string; value: string; onChange: (value: string) => void; placeholder: string }) {
  return (
    <div>
      <label className="mb-2 block text-sm font-medium text-[#424242]">{label}</label>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
      />
    </div>
  );
}

function GenderButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-lg py-3 font-semibold transition-all ${active ? 'bg-[#d84315] text-white shadow-md' : 'border border-[#e0e0e0] bg-white text-[#616161]'}`}
    >
      {children}
    </button>
  );
}

function TermModal({ term, onClose }: { term: (typeof TERM_ITEMS)[number]; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-white/35 p-4 backdrop-blur-sm">
      <div className="flex max-h-[82vh] w-full max-w-2xl flex-col rounded-2xl border border-[#eeeeee] bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-[#eeeeee] px-6 py-5">
          <div>
            <p className="text-xs font-bold text-[#d84315]">약관 확인</p>
            <h2 className="mt-1 text-xl font-bold text-[#212121]">{term.title}</h2>
          </div>
          <button type="button" onClick={onClose} className="rounded-full p-2 text-[#757575] transition-colors hover:bg-[#f5f5f5] hover:text-[#212121]" aria-label="약관 닫기">
            <X size={20} />
          </button>
        </div>
        <div className="overflow-y-auto px-6 py-5">
          <div className="prose prose-sm max-w-none text-[#424242] prose-headings:text-[#212121] prose-strong:text-[#212121]">
            <ReactMarkdown>{term.content}</ReactMarkdown>
          </div>
        </div>
        <div className="border-t border-[#eeeeee] px-6 py-4">
          <button type="button" onClick={onClose} className="w-full rounded-lg bg-[#d84315] py-3 font-bold text-white transition-colors hover:bg-[#bf360c]">
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
