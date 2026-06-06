import { FormEvent, useState } from 'react';
import { Loader2, Send, X } from 'lucide-react';
import { streamAiReport } from '../../api/aiReportApi';
import { streamAiSupport } from '../../api/aiSupportApi';

type ChatMessage = {
  id: number;
  sender: 'bot' | 'user';
  content: string;
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
        },
      ]);

      try {
        const answer = await streamAiSupport(trimmedInput, nextConversationId, (chunk) => {
          setMessages((prev) =>
            prev.map((message) =>
              message.id === botMessageId
                ? { ...message, content: message.content + chunk }
                : message
            )
          );
        });

        if (!answer.trim()) {
          setMessages((prev) =>
            prev.map((message) =>
              message.id === botMessageId
                ? { ...message, content: 'AI 고객센터 답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.' }
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
    const botMessageId = Date.now() + 1;
    setMessages((prev) => [
      ...prev,
      {
        id: botMessageId,
        sender: 'bot',
        content: '',
      },
    ]);

    try {
      const answer = await streamAiReport(trimmedInput, (chunk) => {
        setMessages((prev) =>
          prev.map((message) =>
            message.id === botMessageId
              ? { ...message, content: message.content + chunk }
              : message
          )
        );
      });

      if (!answer.trim()) {
        setMessages((prev) =>
          prev.map((message) =>
            message.id === botMessageId
              ? { ...message, content: '관리자 AI 답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.' }
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

              <div className="max-h-[420px] min-h-72 space-y-4 overflow-y-auto pr-1">
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
                      {message.content}
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

function RiceMascot({ size = 'default', showAdminHat = false }: { size?: 'default' | 'small' | 'large'; showAdminHat?: boolean }) {
  const isSmall = size === 'small';
  const isLarge = size === 'large';
  const containerSize = isLarge ? 'h-16 w-16' : isSmall ? 'h-12 w-12' : 'h-14 w-14';

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
