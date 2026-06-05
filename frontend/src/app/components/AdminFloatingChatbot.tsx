import { FormEvent, useState } from 'react';
import { Loader2, Send, X } from 'lucide-react';
import { AiReportChatResponse, chatAiReport } from '../../api/aiReportApi';
import { AiSupportChatResponse, chatAiSupport } from '../../api/aiSupportApi';

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
  title = '한끼팟 관리자 도우미',
  subtitle = '관리자 도우미',
  botName = '한끼팟',
  greeting = '관리자님, 무엇을 도와드릴까요?',
  initialMessage = '안녕하세요. 한끼팟 관리자 도우미입니다. 궁금한 내용을 입력해 주세요.',
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
      try {
        const response = await chatAiSupport(trimmedInput, conversationId);
        setConversationId(response.data.data.conversationId);
        const botMessage: ChatMessage = {
          id: Date.now() + 1,
          sender: 'bot',
          content: formatAiSupportAnswer(response.data.data),
        };
        setMessages((prev) => [...prev, botMessage]);
      } catch (err: any) {
        const serverMessage = err.response?.data?.message;
        const botMessage: ChatMessage = {
          id: Date.now() + 1,
          sender: 'bot',
          content: serverMessage
            ? `AI 고객센터 요청에 실패했습니다.\n사유: ${serverMessage}`
            : 'AI 고객센터 요청에 실패했습니다. 잠시 후 다시 시도해주세요.',
        };
        setMessages((prev) => [...prev, botMessage]);
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
    try {
      const response = await chatAiReport(trimmedInput);
      const botMessage: ChatMessage = {
        id: Date.now() + 1,
        sender: 'bot',
        content: formatAiReportAnswer(response.data.data),
      };
      setMessages((prev) => [...prev, botMessage]);
    } catch (err: any) {
      const serverMessage = err.response?.data?.message;
      const botMessage: ChatMessage = {
        id: Date.now() + 1,
        sender: 'bot',
        content: serverMessage
          ? `AI 신고 요약 요청에 실패했습니다.\n사유: ${serverMessage}`
          : 'AI 신고 요약 요청에 실패했습니다. 잠시 후 다시 시도해주세요.',
      };
      setMessages((prev) => [...prev, botMessage]);
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

function formatAiReportAnswer(response: AiReportChatResponse) {
  const sections = [response.answer];

  if (response.reportAnalysis) {
    const analysis = response.reportAnalysis;
    sections.push(
      [
        `신고 ID: #${analysis.reportId}`,
        `위험도: ${analysis.riskLevel}`,
        `처리 제안: ${analysis.decisionSuggestion}`,
        `신뢰도: ${analysis.confidenceScore}%`,
        `요약: ${analysis.summary}`,
        `근거: ${analysis.evidence}`,
        `권장 사유: ${analysis.recommendationReason}`,
        `관리자 가이드: ${analysis.actionGuide}`,
      ].join('\n'),
    );
  }

  if (response.highRiskUsers?.highRiskUsers?.length) {
    const highRiskUsers = response.highRiskUsers.highRiskUsers
      .map((user, index) =>
        [
          `${index + 1}. ${user.nickname} (#${user.userId})`,
          `위험도: ${user.riskLevel}`,
          `누적 신고: ${user.totalReportCount}건 / 대기: ${user.pendingReportCount}건 / 채택: ${user.acceptedReportCount}건`,
          `요약: ${user.reasonSummary}`,
          `권장 조치: ${user.recommendedAction}`,
          `관련 신고: ${user.relatedReportIds.join(', ') || '없음'}`,
        ].join('\n'),
      )
      .join('\n\n');

    sections.push(highRiskUsers);
  }

  if (response.fallbackUsed) {
    sections.push('현재 AI 응답은 fallback 결과입니다.');
  }

  return sections.filter(Boolean).join('\n\n');
}

function formatAiSupportAnswer(response: AiSupportChatResponse) {
  const sections = [response.answer];

  sections.push(`문의 분류: ${response.category}`);

  if (response.actionRequired) {
    sections.push('추가 조치가 필요할 수 있습니다. 문제가 계속되면 1:1 문의를 접수해주세요.');
  }

  if (response.fallbackUsed) {
    sections.push('현재 AI 응답은 fallback 결과입니다.');
  }

  return sections.filter(Boolean).join('\n\n');
}
