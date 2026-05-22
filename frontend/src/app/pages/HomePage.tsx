import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Utensils, QrCode, Shield, User, Users } from 'lucide-react';
import { logout } from '../../api/authApi';

export default function HomePage() {
  const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(sessionStorage.getItem('accessToken')));

  useEffect(() => {
    const syncLoginState = () => {
      setIsLoggedIn(Boolean(sessionStorage.getItem('accessToken')));
    };

    window.addEventListener('storage', syncLoginState);
    window.addEventListener('focus', syncLoginState);

    return () => {
      window.removeEventListener('storage', syncLoginState);
      window.removeEventListener('focus', syncLoginState);
    };
  }, []);

  const handleLogout = async () => {
    try {
      await logout();
    } catch (err) {
      console.error('Logout request failed', err);
    } finally {
      sessionStorage.removeItem('accessToken');
      setIsLoggedIn(false);
    }
  };

  return (
      <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
        {/* Header */}
        <header className="bg-white/80 backdrop-blur-sm border-b border-[#e0e0e0] sticky top-0 z-50">
          <div className="max-w-screen-xl mx-auto px-6 h-16 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-3xl">🍚</span>
              <span className="text-2xl font-bold text-[#d84315]">한끼팟</span>
            </div>
            <div className="flex items-center gap-4">
              {isLoggedIn ? (
                  <>
                    <button
                        type="button"
                        onClick={handleLogout}
                        className="px-4 py-2 text-[#616161] hover:text-[#d84315] font-medium transition-colors"
                    >
                      로그아웃
                    </button>
                    <Link
                        to="/me"
                        className="flex items-center gap-2 px-6 py-2.5 bg-[#d84315] text-white rounded-full font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
                    >
                      <User size={18} />
                      내 정보 보기
                    </Link>
                  </>
              ) : (
                  <>
                    <Link
                        to="/login"
                        className="px-4 py-2 text-[#616161] hover:text-[#d84315] font-medium transition-colors"
                    >
                      로그인
                    </Link>
                    <Link
                        to="/signup"
                        className="px-6 py-2.5 bg-[#d84315] text-white rounded-full font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
                    >
                      시작하기
                    </Link>
                  </>
              )}
            </div>
          </div>
        </header>

        {/* Hero Section */}
        <section className="max-w-screen-xl mx-auto px-6 pt-20 pb-24">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <div className="inline-block px-4 py-2 bg-[#fff3e0] rounded-full mb-6">
                <span className="text-[#d84315] font-semibold text-sm">🎓 대학생 식사 매칭 플랫폼</span>
              </div>
              <h1 className="text-5xl lg:text-6xl font-bold text-[#212121] leading-tight mb-6">
                식사로 연결되는<br />
                새로운 만남
              </h1>
              <p className="text-xl text-[#616161] mb-8 leading-relaxed">
                학교 친구들과 함께하는 한 끼.<br />
                책임비 시스템으로 안전하게, QR 인증으로 확실하게.
              </p>
              <div className="flex gap-4">
                <Link
                    to="/posts"
                    className="px-8 py-4 bg-[#d84315] text-white rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-lg hover:shadow-xl hover:scale-105"
                >
                  게시글 둘러보기
                </Link>
                <Link
                    to="/signup"
                    className="px-8 py-4 bg-white text-[#d84315] border-2 border-[#d84315] rounded-xl font-bold text-lg hover:bg-[#fff3e0] transition-all"
                >
                  회원가입
                </Link>
              </div>
            </div>

            <div className="relative">
              <div className="bg-gradient-to-br from-[#d84315] to-[#bf360c] rounded-3xl p-8 shadow-2xl">
                <div className="bg-white rounded-2xl p-6 mb-4">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-[#f5f5f5] rounded-full flex items-center justify-center">
                      <Users size={20} className="text-[#d84315]" />
                    </div>
                    <div>
                      <p className="font-semibold text-[#212121]">밥먹자</p>
                      <p className="text-xs text-[#9e9e9e]">컴퓨터공학과 22학번</p>
                    </div>
                  </div>
                  <p className="text-[#424242] mb-3">오늘 점심 학생식당 같이 가실 분!</p>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-[#616161]">📍 학생식당 1층</span>
                    <span className="font-bold text-[#d84315]">3,000P</span>
                  </div>
                </div>
                <div className="bg-white/20 backdrop-blur-sm rounded-2xl p-4 text-white">
                  <p className="text-sm mb-2">✅ 매칭 완료! 채팅이 시작되었습니다</p>
                  <div className="bg-white/20 rounded-lg px-3 py-2 text-sm">
                    "안녕하세요! 12시 30분에 입구에서 만나요 😊"
                  </div>
                </div>
              </div>
              <div className="absolute -top-4 -right-4 w-24 h-24 bg-[#ff9800] rounded-full blur-3xl opacity-50"></div>
              <div className="absolute -bottom-4 -left-4 w-32 h-32 bg-[#d84315] rounded-full blur-3xl opacity-30"></div>
            </div>
          </div>
        </section>

        {/* Features Section */}
        <section className="bg-[#fffaf5] py-20">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="text-center mb-16">
              <h2 className="text-4xl font-bold text-[#212121] mb-4">왜 한끼팟인가요?</h2>
              <p className="text-lg text-[#616161]">안전하고 확실한 만남을 위한 3가지 핵심 기능</p>
            </div>

            <div className="grid md:grid-cols-3 gap-8">
              <div className="bg-gradient-to-br from-[#fff3e0] to-white rounded-2xl p-8 border border-[#ffe0b2] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#d84315] rounded-2xl flex items-center justify-center mb-6">
                  <Shield size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">책임비 시스템</h3>
                <p className="text-[#616161] leading-relaxed">
                  게시글 작성 시 포인트를 예치하여 노쇼를 방지합니다. 만남 완료 후 전액 반환!
                </p>
              </div>

              <div className="bg-gradient-to-br from-[#e8f5e9] to-white rounded-2xl p-8 border border-[#c8e6c9] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#4caf50] rounded-2xl flex items-center justify-center mb-6">
                  <QrCode size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">QR 만남 인증</h3>
                <p className="text-[#616161] leading-relaxed">
                  실제로 만났는지 QR 코드로 확인합니다. 투명하고 공정한 매칭 시스템!
                </p>
              </div>

              <div className="bg-gradient-to-br from-[#e3f2fd] to-white rounded-2xl p-8 border border-[#bbdefb] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#2196f3] rounded-2xl flex items-center justify-center mb-6">
                  <Utensils size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">학교 인증 커뮤니티</h3>
                <p className="text-[#616161] leading-relaxed">
                  .ac.kr 이메일 인증으로 같은 학교 친구들과만 매칭됩니다. 안전한 만남!
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* How it works */}
        <section className="py-20 bg-gradient-to-br from-[#eef8ff] to-[#fffaf5]">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="text-center mb-16">
              <h2 className="text-4xl font-bold text-[#212121] mb-4">이용 방법</h2>
              <p className="text-lg text-[#616161]">3단계로 간편하게 식사 메이트를 찾아보세요</p>
            </div>

            <div className="grid md:grid-cols-3 gap-8">
              <div className="relative">
                <div className="text-center">
                  <div className="w-16 h-16 bg-[#d84315] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                    1
                  </div>
                  <h3 className="text-xl font-bold text-[#212121] mb-3">게시글 작성 또는 신청</h3>
                  <p className="text-[#616161]">
                    먹고 싶은 시간과 장소를 올리거나<br />
                    원하는 게시글에 신청하세요
                  </p>
                </div>
                <div className="hidden md:block absolute top-8 -right-4 w-8 h-0.5 bg-[#e0e0e0]"></div>
              </div>

              <div className="relative">
                <div className="text-center">
                  <div className="w-16 h-16 bg-[#ff9800] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                    2
                  </div>
                  <h3 className="text-xl font-bold text-[#212121] mb-3">1:1 채팅으로 약속</h3>
                  <p className="text-[#616161]">
                    매칭되면 채팅방이 생성됩니다<br />
                    메뉴와 만날 시간을 정하세요
                  </p>
                </div>
                <div className="hidden md:block absolute top-8 -right-4 w-8 h-0.5 bg-[#e0e0e0]"></div>
              </div>

              <div className="text-center">
                <div className="w-16 h-16 bg-[#4caf50] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                  3
                </div>
                <h3 className="text-xl font-bold text-[#212121] mb-3">QR 인증 후 만남</h3>
                <p className="text-[#616161]">
                  약속 장소에서 QR 코드로 인증<br />
                  즐거운 식사 시간을 보내세요!
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section className="py-20 bg-gradient-to-r from-[#d84315] to-[#bf360c]">
          <div className="max-w-screen-xl mx-auto px-6 text-center">
            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6">
              지금 바로 시작하세요
            </h2>
            <p className="text-xl text-white/90 mb-8">
              회원가입하고 10,000P 보너스 받기
            </p>
            <Link
                to="/signup"
                className="inline-block px-10 py-4 bg-white text-[#d84315] rounded-xl font-bold text-lg hover:bg-[#f5f5f5] transition-all shadow-xl hover:shadow-2xl hover:scale-105"
            >
              무료로 시작하기 →
            </Link>
          </div>
        </section>

        {/* Footer */}
        <footer className="bg-[#212121] text-white py-12">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="flex flex-col md:flex-row justify-between items-center gap-6">
              <div className="flex items-center gap-2">
                <span className="text-2xl">🍚</span>
                <span className="text-xl font-bold">한끼팟</span>
              </div>
              <div className="flex gap-6 text-sm text-[#bdbdbd]">
                <a href="#" className="hover:text-white transition-colors">서비스 이용약관</a>
                <a href="#" className="hover:text-white transition-colors">개인정보 처리방침</a>
                <a href="#" className="hover:text-white transition-colors">문의하기</a>
              </div>
            </div>
            <div className="mt-8 pt-6 border-t border-[#424242] text-center text-sm text-[#9e9e9e]">
              © 2026 한끼팟. All rights reserved.
            </div>
          </div>
        </footer>
      </div>
  );
}
