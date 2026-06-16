import { FormEvent, useEffect, useRef, useState } from 'react';
import { Loader2, Send, X } from 'lucide-react';
import { streamAiReport } from '../../api/aiReportApi';
import { streamAiSupport } from '../../api/aiSupportApi';

type ChatMessage = {
  id: number;
  sender: 'bot' | 'user';
  content: string;
  isThinking?: boolean;
};

type FloatingChatbotProps = {
  title?: string;
  subtitle?: string;
  botName?: string;
  greeting?: string;
  initialMessage?: string;
  replyMessage?: string;
  showAdminHat?: boolean;
  useAiReportApi?: boolean;
  useAiSupportApi?: boolean;
};

export default function AdminFloatingChatbot({
  title = '한끼팟 관리자 AI',
  subtitle = '콘솔 Advisor',
  botName = '한끼팟',
  greeting = '관리자님, 콘솔 현황과 운영 정책을 물어보세요.',
  initialMessage = '안녕하세요. 한끼팟 관리자 AI입니다. 게시글, 신고, 문의, 유저, 결제, FAQ 현황이나 처리 기준을 질문해 주세요.',
  replyMessage = '확인했습니다. 관리자 콘솔에서 필요한 내용을 차근차근 도와드릴게요.',
  showAdminHat = true,
  useAiReportApi = true,
  useAiSupportApi = false,
}: FloatingChatbotProps) {
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 1,
      sender: 'bot',
      content: initialMessage,
    },
  ]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const messageList = messageListRef.current;
    if (!messageList) {
      return;
    }

    messageList.scrollTo({
      top: messageList.scrollHeight,
      behavior: 'smooth',
    });
  }, [isOpen, messages]);

  const showThinkingForMoment = () => new Promise((resolve) => setTimeout(resolve, 800));
  const revealBotMessage = (messageId: number, text: string) =>
    new Promise<void>((resolve) => {
      const chars = Array.from(text);

      if (chars.length === 0) {
        resolve();
        return;
      }

      let index = 0;
      const revealNext = () => {
        const nextText = chars[index];
        index += 1;

        setMessages((prev) =>
          prev.map((message) =>
            message.id === messageId
              ? { ...message, content: message.content + nextText, isThinking: false }
              : message
          )
        );

        if (index < chars.length) {
          window.setTimeout(revealNext, 45);
          return;
        }

        resolve();
      };

      revealNext();
    });

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedInput = input.trim();
    if (!trimmedInput || isSending) {
      return;
    }

    const userMessage: ChatMessage = {
      id: Date.now(),
      sender: 'user',
      content: trimmedInput,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');

    if (useAiSupportApi) {
      setIsSending(true);
      const nextConversationId = conversationId ?? crypto.randomUUID();
      const botMessageId = Date.now() + 1;
      setConversationId(nextConversationId);
      setMessages((prev) => [
        ...prev,
        {
          id: botMessageId,
          sender: 'bot',
          content: '',
          isThinking: true,
        },
      ]);

      try {
        await showThinkingForMoment();
        let typingQueue = Promise.resolve();
        const answer = await streamAiSupport(trimmedInput, nextConversationId, (chunk) => {
          typingQueue = typingQueue.then(() => revealBotMessage(botMessageId, chunk));
        });
        await typingQueue;

        if (!answer.trim()) {
          setMessages((prev) =>
            prev.map((message) =>
              message.id === botMessageId
                ? { ...message, content: 'AI 고객센터 답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.', isThinking: false }
                : message
            )
          );
        }
      } catch (err: any) {
        setMessages((prev) =>
          prev.map((message) =>
            message.id === botMessageId
              ? {
                ...message,
                content: err.message
                  ? `AI 고객센터 요청에 실패했습니다.\n사유: ${err.message}`
                  : 'AI 고객센터 요청에 실패했습니다. 잠시 후 다시 시도해주세요.',
                isThinking: false,
              }
              : message
          )
        );
      } finally {
        setIsSending(false);
      }
      return;
    }

    if (!useAiReportApi) {
      const botMessage: ChatMessage = {
        id: Date.now() + 1,
        sender: 'bot',
        content: replyMessage,
      };
      setMessages((prev) => [...prev, botMessage]);
      return;
    }

    setIsSending(true);
    const nextConversationId = conversationId ?? crypto.randomUUID();
    const botMessageId = Date.now() + 1;
    setConversationId(nextConversationId);
    setMessages((prev) => [
      ...prev,
      {
        id: botMessageId,
        sender: 'bot',
        content: '',
        isThinking: true,
      },
    ]);

    try {
      await showThinkingForMoment();
      let typingQueue = Promise.resolve();
      const answer = await streamAiReport(trimmedInput, nextConversationId, (chunk) => {
        typingQueue = typingQueue.then(() => revealBotMessage(botMessageId, chunk));
      });
      await typingQueue;

      if (!answer.trim()) {
        setMessages((prev) =>
          prev.map((message) =>
            message.id === botMessageId
              ? { ...message, content: '관리자 AI 답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.', isThinking: false }
              : message
          )
        );
      }
    } catch (err: any) {
      setMessages((prev) =>
        prev.map((message) =>
          message.id === botMessageId
            ? {
              ...message,
              content: err.message
                ? `관리자 AI 요청에 실패했습니다.\n사유: ${err.message}`
                : '관리자 AI 요청에 실패했습니다. 잠시 후 다시 시도해주세요.',
              isThinking: false,
            }
            : message
        )
      );
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="fixed bottom-24 right-6 z-[80]">
      {isOpen && (
        <section className="mb-6 w-[min(520px,calc(100vw-32px))] overflow-hidden rounded-[34px] border border-[#f0e1d2] bg-[#fffaf4] shadow-2xl">
          <div className="flex items-center justify-between border-b border-[#f2e4d6] px-7 py-6">
            <div className="flex items-center gap-4">
              <RiceMascot showAdminHat={showAdminHat} />
              <div>
                <p className="text-2xl font-extrabold text-[#2a211b]">{title}</p>
                <p className="text-sm font-semibold text-[#9a7a62]">{subtitle}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="flex h-12 w-12 items-center justify-center rounded-full bg-white text-[#8d6e63] shadow-sm transition-colors hover:bg-[#fff3e0] hover:text-[#d84315]"
              aria-label="챗봇 닫기"
            >
              <X size={26} />
            </button>
          </div>

          <div className="bg-[#fffaf4] p-6">
            <div className="rounded-[28px] bg-white p-6 shadow-sm">
              <div className="mb-5 flex items-start gap-4">
                <RiceMascot size="small" showAdminHat={showAdminHat} />
                <div>
                  <p className="text-lg font-extrabold text-[#2a211b]">{botName}</p>
                  <p className="mt-1 text-base leading-7 text-[#4e3c32]">
                    {greeting}
                  </p>
                </div>
              </div>

              <div ref={messageListRef} className="max-h-[420px] min-h-72 space-y-4 overflow-y-auto pr-1">
                {messages.map((message) => (
                  <div
                    key={message.id}
                    className={`flex ${message.sender === 'user' ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-[84%] whitespace-pre-wrap rounded-[22px] px-5 py-3.5 text-base leading-7 ${
                        message.sender === 'user'
                          ? 'bg-[#d84315] text-white'
                          : 'bg-[#fff3e0] text-[#3d2b22]'
                      }`}
                    >
                      {message.sender === 'bot' && (
                        <div className="mb-2 flex items-center gap-2 text-sm font-extrabold text-[#d84315]">
                          <RiceMascot size="tiny" showAdminHat={showAdminHat} />
                          {botName}
                        </div>
                      )}
                      {message.isThinking && !message.content ? <ThinkingIndicator /> : formatChatContent(message.content)}
                    </div>
                  </div>
                ))}
              </div>

              <form onSubmit={handleSubmit} className="mt-5 flex gap-3">
                <input
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="메시지를 입력하세요"
                  disabled={isSending}
                  className="h-14 min-w-0 flex-1 rounded-2xl border border-[#ead8c5] px-4 text-base outline-none transition-colors focus:border-[#d84315]"
                />
                <button
                  type="submit"
                  disabled={isSending}
                  className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#ff7043] text-white transition-colors hover:bg-[#d84315]"
                  aria-label="메시지 전송"
                >
                  {isSending ? <Loader2 className="animate-spin" size={24} /> : <Send size={24} />}
                </button>
              </form>
            </div>

          </div>
        </section>
      )}

      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex h-20 w-20 items-center justify-center rounded-full bg-white shadow-2xl ring-1 ring-[#f0e1d2] transition-transform hover:scale-105"
        aria-label={isOpen ? '챗봇 닫기' : '챗봇 열기'}
      >
        {isOpen ? <X size={38} className="text-[#6d5a50]" /> : <RiceMascot size="large" showAdminHat={showAdminHat} />}
      </button>
    </div>
  );
}

function ThinkingIndicator() {
  return (
    <div className="inline-flex items-center gap-3 text-sm font-semibold text-[#8d6e63]">
      <span>답변을 준비하고 있어요</span>
      <span className="flex items-center gap-1" aria-hidden="true">
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315] [animation-delay:-0.2s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315] [animation-delay:-0.1s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315]" />
      </span>
    </div>
  );
}

function formatChatContent(content: string) {
  return normalizeChatReadability(content)
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/([^\n])#{1,6}\s*/g, '$1\n\n')
    .replace(/출처\s*:\s*출처\s*없음/g, '')
    .replace(/출처\s*:\s*-\s*출처\s*없음/g, '')
    .replace(/출처\s*:\s*-\s*출처없음/g, '')
    .replace(/출처\s*:\s*-\s*없음/g, '')
    .replace(/출처\s*:\s*출처없음/g, '')
    .replace(/출처\s*:\s*없음/g, '')
    .replace(/\n-\s*출처\s*없음/g, '')
    .replace(/\n-\s*출처없음/g, '')
    .replace(/\n-\s*없음\s*$/g, '')
    .replace(/\n-\s*문서명\s*/g, '')
    .replace(/\n-\s*\[?REPORT RAG 출처[^\n]*/g, '')
    .replace(/\n-\s*제공된 정책명\s*/g, '')
    .replace(/\n\s*출처:\s*$/g, '')
    .replace(/\n\s*출처:\s*\n\s*$/g, '')
    .replace(/^\s*신고\s*분석\s*핵심\s*요약\s*:\s*/g, '신고 분석\n\n핵심 요약:\n')
    .replace(/^\s*이의제기\s*검토\s*핵심\s*요약\s*:\s*/g, '이의제기 검토\n\n핵심 요약:\n')
    .replace(/^\s*(\d+번\s*이의제기\s*검토\s*정보를\s*조회했습니다\.)/g, '이의제기 검토\n\n$1')
    .replace(/([^\n])\s*(핵심 요약:)/g, '$1\n\n$2\n')
    .replace(/([^\n])\s*(관리자 확인 항목:)/g, '$1\n\n$2\n')
    .replace(/([^\n])\s*(판단 근거:)/g, '$1\n\n$2\n')
    .replace(/([^\n])\s*(판단 방향:)/g, '$1\n\n$2\n')
    .replace(/([^\n])\s*(관리자 액션:)/g, '$1\n\n$2\n')
    .replace(/([^\n])\s*(다음 조치:)/g, '$1\n\n$2\n')
    .replace(/([^\n])(\d+\.\s*)/g, '$1\n$2')
    .replace(/([^\n])(출처:)/g, '$1\n\n$2')
    .replace(/출처:\s*-\s*/g, '출처:\n- ')
    .replace(/([^\n])-\s*(?=[가-힣A-Za-z])/g, '$1\n- ')
    .replace(/([.!?])\s*-\s*/g, '$1\n- ')
    .replace(/([^\n])(- rag-docs\/)/g, '$1\n- rag-docs/')
    .replace(/([^\n])(- (?:report|support|matching)\/)/g, '$1\n$2')
    .replace(/\n{3,}/g, '\n\n')
    .trimStart();
}

function normalizeChatReadability(content: string) {
  const phraseRules: Array<[RegExp, string]> = [
    [/신고분석/g, '신고 분석'],
    [/핵심요약/g, '핵심 요약'],
    [/관리자 확인항목/g, '관리자 확인 항목'],
    [/확인항목/g, '확인 항목'],
    [/이의제기검토/g, '이의제기 검토'],
    [/검토정보를조회/g, '검토 정보를 조회'],
    [/검토정보/g, '검토 정보'],
    [/매칭ID/g, '매칭 ID'],
    [/이의제기ID/g, '이의제기 ID'],
    [/이의제기유형/g, '이의제기 유형'],
    [/현재상태/g, '현재 상태'],
    [/만남 인증상태/g, '만남 인증 상태'],
    [/만남인증상태/g, '만남 인증 상태'],
    [/제출사유/g, '제출 사유'],
    [/판단근거/g, '판단 근거'],
    [/판단방향/g, '판단 방향'],
    [/관리자액션/g, '관리자 액션'],
    [/다음조치/g, '다음 조치'],
    [/신고사유/g, '신고 사유'],
    [/신고자는/g, '신고자는'],
    [/피신고자는/g, '피신고자는'],
    [/현재신고상태/g, '현재 신고 상태'],
    [/대기중/g, '대기 중'],
    [/신고상태/g, '신고 상태'],
    [/같은모집글/g, '같은 모집글'],
    [/반복적으로올리는/g, '반복적으로 올리는'],
    [/사용자에대한/g, '사용자에 대한'],
    [/신고입니다/g, '신고입니다'],
    [/신고고입니다/g, '신고입니다'],
    [/신고자와피신고자/g, '신고자와 피신고자'],
    [/신고상세내용/g, '신고 상세 내용'],
    [/채팅기록/g, '채팅 기록'],
    [/대상게시글/g, '대상 게시글'],
    [/피신고자의신고이력/g, '피신고자의 신고 이력'],
    [/신고이력/g, '신고 이력'],
    [/반복적인스팸/g, '반복적인 스팸'],
    [/게시글여부/g, '게시글 여부'],
    [/확인후/g, '확인 후'],
    [/신고채택/g, '신고 채택'],
    [/여부를결정/g, '여부를 결정'],
    [/수있습니다/g, '수 있습니다'],
    [/확정하지않으므로/g, '확정하지 않으므로'],
    [/추가검토/g, '추가 검토'],
    [/판단기준/g, '판단 기준'],
    [/조치를취하세요/g, '조치를 취하세요'],
    [/관리자의추가/g, '관리자의 추가'],
    [/대해안내/g, '대해 안내'],
    [/약속된만남/g, '약속된 만남'],
    [/참석하지않는/g, '참석하지 않는'],
    [/경우를말하며/g, '경우를 말하며'],
    [/이경우/g, '이 경우'],
    [/확정되기전/g, '확정되기 전'],
    [/이의제기절차/g, '이의제기 절차'],
    [/노쇼예정상태/g, '노쇼 예정 상태'],
    [/노쇼예정알림발송시점/g, '노쇼 예정 알림 발송 시점'],
    [/24시간이내/g, '24시간 이내'],
    [/이내에가능/g, '이내에 가능'],
    [/장소인증/g, '장소 인증'],
    [/완료한사용자/g, '완료한 사용자'],
    [/이의제기대상/g, '이의제기 대상'],
    [/증빙자료/g, '증빙 자료'],
    [/위치정보/g, '위치 정보'],
    [/포함된사진/g, '포함된 사진'],
    [/지도앱/g, '지도 앱'],
    [/위치캡처/g, '위치 캡처'],
    [/GPS오류/g, 'GPS 오류'],
    [/QR오류/g, 'QR 오류'],
    [/QR스캔/g, 'QR 스캔'],
    [/QR화면/g, 'QR 화면'],
    [/스캔오류화면/g, '스캔 오류 화면'],
    [/오류화면/g, '오류 화면'],
    [/상대방QR/g, '상대방 QR'],
    [/찍은사진/g, '찍은 사진'],
    [/오류메시지/g, '오류 메시지'],
    [/메시지캡처/g, '메시지 캡처'],
    [/최종판정/g, '최종 판정'],
    [/관리자검토/g, '관리자 검토'],
    [/정확한정책확인/g, '정확한 정책 확인'],
    [/1:1문의/g, '1:1 문의'],
    [/매칭취소/g, '매칭 취소'],
    [/매칭신청/g, '매칭 신청'],
    [/매칭상태/g, '매칭 상태'],
    [/책임비/g, '책임비'],
    [/포인트환불/g, '포인트 환불'],
    [/포인트충전/g, '포인트 충전'],
    [/거래내역/g, '거래 내역'],
    [/결제내역/g, '결제 내역'],
    [/유료포인트/g, '유료 포인트'],
    [/예치포인트/g, '예치 포인트'],
    [/학교인증/g, '학교 인증'],
    [/계정상태/g, '계정 상태'],
    [/신고처리/g, '신고 처리'],
    [/신고접수/g, '신고 접수'],
    [/신고보상/g, '신고 보상'],
    [/재신고/g, '재신고'],
    [/계정제재/g, '계정 제재'],
    [/고객센터/g, '고객센터'],
    [/문의접수/g, '문의 접수'],
    [/처리기한/g, '처리 기한'],
    [/추가확인/g, '추가 확인'],
    [/관리자확인/g, '관리자 확인'],
  ];

  return phraseRules
    .reduce((text, [pattern, replacement]) => text.replace(pattern, replacement), content)
    .replace(/및(?=[가-힣])/g, ' 및 ')
    .replace(/([^\d\s]),\s*(?=\S)/g, '$1, ')
    .replace(/([가-힣A-Za-z0-9])\s*:\s*(?=\S)/g, '$1: ')
    .replace(/([.!?])(?=[가-힣A-Za-z0-9])/g, '$1 ')
    .replace(/([가-힣])(\d+\.\s*)/g, '$1\n$2')
    .replace(/(\d+)\.\s*(?=[가-힣A-Za-z])/g, '$1. ')
    .replace(/([가-힣])([A-Z]{2,})/g, '$1 $2')
    .replace(/([A-Z]{2,})([가-힣])/g, '$1 $2')
    .replace(/[ \t]{2,}/g, ' ');
}

function RiceMascot({ size = 'default', showAdminHat = false }: { size?: 'default' | 'small' | 'large' | 'tiny'; showAdminHat?: boolean }) {
  const isSmall = size === 'small';
  const isLarge = size === 'large';
  const isTiny = size === 'tiny';
  const containerSize = isLarge ? 'h-16 w-16' : isSmall ? 'h-12 w-12' : isTiny ? 'h-7 w-7' : 'h-14 w-14';

  return (
    <div className={`${containerSize} relative shrink-0 rounded-3xl bg-[#fff7ed]`}>
      {showAdminHat && (
        <>
          <span className="absolute left-1/2 top-[3%] z-10 h-[18%] w-[42%] -translate-x-1/2 rounded-t-lg bg-[#3d2b22]" />
          <span className="absolute left-1/2 top-[18%] z-10 h-[9%] w-[58%] -translate-x-1/2 rounded-full bg-[#2a211b]" />
          <span className="absolute left-[54%] top-[6%] z-20 h-[7%] w-[7%] rounded-full bg-[#ff7043]" />
        </>
      )}
      <span className="absolute left-[8%] top-[34%] h-[50%] w-[50%] rounded-full bg-[#ff9f43] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute right-[8%] top-[34%] h-[50%] w-[50%] rounded-full bg-[#4fc3c7] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute left-1/2 top-[15%] h-[56%] w-[56%] -translate-x-1/2 rounded-full bg-[#8bc34a] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute bottom-[15%] left-1/2 h-[32%] w-[66%] -translate-x-1/2 rounded-b-full rounded-t-md bg-white shadow-[0_2px_0_#7b4b2a]" />
      <span className="absolute left-[38%] top-[38%] h-[10%] w-[10%] rounded-full bg-[#3d2b22]" />
      <span className="absolute right-[38%] top-[38%] h-[10%] w-[10%] rounded-full bg-[#3d2b22]" />
    </div>
  );
}
