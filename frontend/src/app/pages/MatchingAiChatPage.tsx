import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowRight, Bot, CalendarClock, CheckCircle2, Coins, Loader2, Send, Sparkles, XCircle } from 'lucide-react';
import { clearMatchingConversationOnExit, RecommendedPost, streamMatchingChat } from '@/api/aiApi';

const EXAMPLE_QUESTIONS = [
  '책임비 낮은 식사팟 추천해줘',
  '조용한 사람으로 추천해줘',
  '활발한 사람으로 추천해줘',
  '오늘 오후 3시 근처에 모집 중인 식사팟 추천해줘',
  '치킨',
  '국밥',
  '파스타',
  '돈까스',
  '쌀국수',
  '라멘',
  '덮밥',
  '혼자 먹기 싫어',
  '든든하게 먹을 사람',
];

type ChatMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  isThinking?: boolean;
  fallbackUsed?: boolean;
  recommendedPosts?: RecommendedPost[];
};

type ParsedRecommendation = {
  postId: number;
  placeName?: string;
  meetAt?: string;
  deposit?: string;
  reason?: string;
};

export default function MatchingAiChatPage() {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const conversationIdRef = useRef<string | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: Date.now(),
      role: 'assistant',
      content: '원하는 시간, 메뉴, 분위기, 책임비 조건을 말해주면 어울리는 식사팟을 찾아드릴게요.',
      recommendedPosts: [],
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const messageList = messageListRef.current;
    if (!messageList) {
      return;
    }

    messageList.scrollTo({
      top: messageList.scrollHeight,
      behavior: 'smooth',
    });
  }, [messages]);

  useEffect(() => {
    return () => {
      const activeConversationId = conversationIdRef.current;

      if (activeConversationId) {
        clearMatchingConversationOnExit(activeConversationId);
      }
    };
  }, []);

  const showThinkingForMoment = () => new Promise((resolve) => setTimeout(resolve, 800));

  const submitMessage = async (message: string) => {
    const trimmed = message.trim();
    if (!trimmed || loading) return;

    const userMessage: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content: trimmed,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setError('');
    setLoading(true);

    const nextConversationId = conversationId ?? crypto.randomUUID();
    const assistantMessageId = Date.now() + 1;
    setConversationId(nextConversationId);
    conversationIdRef.current = nextConversationId;

    setMessages((prev) => [
      ...prev,
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        isThinking: true,
        recommendedPosts: [],
      },
    ]);

    try {
      await showThinkingForMoment();
      const response = await streamMatchingChat({
        conversationId: nextConversationId,
        message: trimmed,
      }, (chunk) => {
        setMessages((prev) =>
          prev.map((item) =>
            item.id === assistantMessageId
              ? { ...item, content: item.content + chunk, isThinking: false }
              : item
          )
        );
      });

      setMessages((prev) =>
        prev.map((item) =>
          item.id === assistantMessageId
            ? {
              ...item,
              content: response.answer || item.content,
              isThinking: false,
              recommendedPosts: response.recommendedPosts ?? [],
              fallbackUsed: response.fallbackUsed,
            }
            : item
        )
      );

      if (!response.answer.trim()) {
        setMessages((prev) =>
          prev.map((item) =>
            item.id === assistantMessageId
              ? { ...item, content: '조건에 맞는 답변을 생성하지 못했어요. 잠시 후 다시 시도해주세요.', isThinking: false }
              : item
          )
        );
      }
    } catch (err: any) {
      console.error('AI matching chat failed', err);
      setError(err.message || '추천을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      setMessages((prev) =>
        prev.map((item) =>
          item.id === assistantMessageId
            ? {
              ...item,
              content: '지금은 추천 결과를 가져오지 못했어요. 조건을 조금 바꾸거나 잠시 후 다시 요청해주세요.',
              isThinking: false,
              recommendedPosts: [],
            }
            : item
        )
      );
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    submitMessage(input);
  };

  return (
    <div className="mx-auto max-w-4xl">
      <section className="overflow-hidden rounded-[34px] border border-[#f0e1d2] bg-[#fffaf4] shadow-2xl">
        <div className="flex flex-col gap-5 border-b border-[#f2e4d6] px-6 py-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            <RiceMascot />
            <div>
              <div className="mb-1 inline-flex items-center gap-1.5 rounded-full bg-[#fff3e0] px-3 py-1 text-xs font-bold text-[#ef6c00]">
                <Sparkles size={14} />
                AI 매칭 추천
              </div>
              <h1 className="text-3xl font-extrabold text-[#2a211b]">한끼팟 매칭 AI</h1>
              <p className="mt-1 text-sm font-semibold text-[#9a7a62]">
                원하는 시간, 메뉴, 분위기를 말하면 어울리는 식사팟을 찾아드릴게요.
              </p>
            </div>
          </div>
          <Link
            to="/posts"
            className="inline-flex h-11 items-center justify-center rounded-2xl border border-[#ead8c5] bg-white px-4 text-sm font-bold text-[#6d5a50] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            게시글 보기
          </Link>
        </div>

        <div className="p-6">
          <div className="rounded-[28px] bg-white p-6 shadow-sm">
            <div className="mb-5 flex items-start gap-4">
              <RiceMascot size="small" />
              <div>
                <p className="text-lg font-extrabold text-[#2a211b]">한끼팟</p>
                <p className="mt-1 text-base leading-7 text-[#4e3c32]">
                  조건을 편하게 입력해 주세요. 현재 신청 가능한 식사팟 중심으로 추천할게요.
                </p>
              </div>
            </div>

            <div className="mb-5 rounded-2xl bg-[#fffaf4] p-4">
              <div className="mb-3 flex items-center gap-2 text-sm font-bold text-[#4e3c32]">
                <Bot size={17} className="text-[#d84315]" />
                추천 질문
              </div>
              <div className="flex flex-wrap gap-2">
                {EXAMPLE_QUESTIONS.map((question) => (
                  <button
                    key={question}
                    type="button"
                    onClick={() => submitMessage(question)}
                    disabled={loading}
                    className="rounded-full border border-[#ead8c5] bg-white px-3 py-2 text-xs font-semibold text-[#6d5a50] transition-colors hover:border-[#d84315] hover:bg-[#fff3e0] hover:text-[#d84315] disabled:opacity-60"
                  >
                    {question}
                  </button>
                ))}
              </div>
            </div>

            {error && (
              <div className="mb-4 flex items-start gap-2 rounded-2xl border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
                <AlertCircle size={18} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div ref={messageListRef} className="h-[520px] overflow-y-auto pr-1">
              <div className="space-y-4">
                {messages.map((message) => (
                  <ChatBubble key={message.id} message={message} />
                ))}
              </div>
            </div>

            <form onSubmit={handleSubmit} className="mt-5 flex gap-3">
              <input
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="조건을 입력하면 어울리는 식사팟을 추천해드려요."
                disabled={loading}
                className="h-14 min-w-0 flex-1 rounded-2xl border border-[#ead8c5] px-4 text-base outline-none transition-colors focus:border-[#d84315]"
              />
              <button
                type="submit"
                disabled={loading || input.trim().length === 0}
                className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#ff7043] text-white transition-colors hover:bg-[#d84315] disabled:bg-[#e0e0e0]"
                aria-label="메시지 전송"
                title="메시지 전송"
              >
                {loading ? <Loader2 size={22} className="animate-spin" /> : <Send size={24} />}
              </button>
            </form>
          </div>
        </div>
      </section>
    </div>
  );
}

function ChatBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';
  const displayRecommendedPosts = getDisplayRecommendedPosts(message);
  const parsedRecommendations = !isUser && displayRecommendedPosts.length === 0
      ? parseRecommendationCards(message.content)
      : [];
  const linkedPostIds = !isUser && displayRecommendedPosts.length === 0 && parsedRecommendations.length === 0
      ? getMentionedPostIds(message.content)
      : [];
  const hasRecommendationCards = !isUser && (
      displayRecommendedPosts.length > 0 ||
      parsedRecommendations.length > 0 ||
      linkedPostIds.length > 0
  );
  const hasStructuredRecommendationText = displayRecommendedPosts.length > 0 || parsedRecommendations.length > 0;
  const displayText = hasStructuredRecommendationText
      ? getRecommendationIntro(message.content)
      : formatMatchingContent(message.content);
  const recommendationReasons = getRecommendationReasons(displayRecommendedPosts, parsedRecommendations);

  return (
      <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
        <div className={`max-w-[92%] ${isUser ? 'sm:max-w-[72%]' : 'w-full'}`}>
          <div
              className={`rounded-[22px] px-5 py-3.5 text-base leading-7 ${
                  isUser
                      ? 'bg-[#d84315] text-white'
                      : 'bg-[#fff3e0] text-[#3d2b22]'
              }`}
          >
            {!isUser && (
                <div className="mb-2 flex items-center gap-2 text-sm font-extrabold text-[#d84315]">
                  <RiceMascot size="tiny" />
                  한끼팟
                </div>
            )}
            {message.isThinking && !message.content ? (
                <ThinkingIndicator />
            ) : !hasRecommendationCards ? (
                <p className="whitespace-pre-wrap">{displayText}</p>
            ) : null}
            {message.fallbackUsed && (
                <div className="mt-3 rounded-lg border border-[#ffcc80] bg-[#fff8e1] px-3 py-2 text-xs font-semibold text-[#ef6c00]">
                  AI 추천이 일부 제한된 상태입니다.
                </div>
            )}

            {!isUser && displayRecommendedPosts.length > 0 && (
                <div className="mt-4 border-t border-[#eeeeee] pt-3">
                  <RecommendationIntro message={displayText} reasons={recommendationReasons} />
                  <p className="mb-2 text-xs font-bold text-[#616161]">추천 게시글</p>
                  <div className="grid gap-2">
                    {displayRecommendedPosts.map((post) => (
                        <RecommendedPostMiniCard key={post.postId} post={post} />
                    ))}
                  </div>
                </div>
            )}

            {!isUser && parsedRecommendations.length > 0 && (
                <div className="mt-4 border-t border-[#eeeeee] pt-3">
                  <RecommendationIntro message={displayText} reasons={recommendationReasons} />
                  <p className="mb-2 text-xs font-bold text-[#616161]">추천 게시글</p>
                  <div className="grid gap-2">
                    {parsedRecommendations.map((post) => (
                        <ParsedRecommendationCard key={post.postId} post={post} />
                    ))}
                  </div>
                </div>
            )}

            {!isUser && linkedPostIds.length > 0 && (
                <div className="mt-4 border-t border-[#eeeeee] pt-3">
                  <RecommendationIntro message={displayText} reasons={recommendationReasons} />
                  <p className="mb-2 text-xs font-bold text-[#616161]">추천 게시글</p>
                  <div className="grid gap-2 sm:grid-cols-3">
                    {linkedPostIds.map((postId) => (
                        <Link
                            key={postId}
                            to={`/posts/${postId}`}
                            className="group inline-flex min-w-0 items-center justify-center gap-1.5 rounded-xl border border-[#e0e0e0] bg-white px-3 py-2 text-xs font-bold text-[#424242] transition-all hover:border-[#d84315] hover:bg-[#fff8f3] hover:text-[#d84315]"
                        >
                          <span>#{postId} 상세</span>
                          <ArrowRight size={13} className="shrink-0 transition-transform group-hover:translate-x-0.5" />
                        </Link>
                    ))}
                  </div>
                </div>
            )}
          </div>

          {!isUser && displayRecommendedPosts.length === 0 && parsedRecommendations.length === 0 && linkedPostIds.length === 0 && hasRecommendedAnswer(message) && !isClarificationQuestion(message.content) && (
              <div className="mt-3 rounded-2xl border border-[#eeeeee] bg-white p-3">
                <p className="text-sm font-bold text-[#212121]">게시글에서 직접 확인하기</p>
                <p className="mt-0.5 text-xs text-[#9e9e9e]">
                  추천 후보 카드가 없는 응답입니다. 게시글 목록에서 조건에 맞는 모집글을 확인해보세요.
                </p>
                <Link
                    to="/posts"
                    className="mt-3 inline-flex w-full items-center justify-center gap-1.5 rounded-lg bg-[#d84315] px-3 py-2 text-xs font-bold text-white transition-colors hover:bg-[#bf360c]"
                >
                  게시글 목록으로 이동
                  <ArrowRight size={13} />
                </Link>
              </div>
          )}
        </div>
      </div>
  );
}

function RiceMascot({ size = 'default' }: { size?: 'default' | 'small' | 'tiny' }) {
  const containerSize = size === 'tiny' ? 'h-7 w-7' : size === 'small' ? 'h-12 w-12' : 'h-16 w-16';

  return (
    <div className={`${containerSize} relative shrink-0 rounded-3xl bg-[#fff7ed]`}>
      <span className="absolute left-[8%] top-[34%] h-[50%] w-[50%] rounded-full bg-[#ff9f43] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute right-[8%] top-[34%] h-[50%] w-[50%] rounded-full bg-[#4fc3c7] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute left-1/2 top-[15%] h-[56%] w-[56%] -translate-x-1/2 rounded-full bg-[#8bc34a] shadow-[inset_0_-2px_0_rgba(0,0,0,0.12)]" />
      <span className="absolute bottom-[15%] left-1/2 h-[32%] w-[66%] -translate-x-1/2 rounded-b-full rounded-t-md bg-white shadow-[0_2px_0_#7b4b2a]" />
      <span className="absolute left-[38%] top-[38%] h-[10%] w-[10%] rounded-full bg-[#3d2b22]" />
      <span className="absolute right-[38%] top-[38%] h-[10%] w-[10%] rounded-full bg-[#3d2b22]" />
    </div>
  );
}

function ThinkingIndicator() {
  return (
      <div className="inline-flex items-center gap-3 text-sm font-semibold text-[#8d6e63]">
        <span>추천 조건을 살펴보고 있어요</span>
        <span className="flex items-center gap-1" aria-hidden="true">
          <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315] [animation-delay:-0.2s]" />
          <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315] [animation-delay:-0.1s]" />
          <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#d84315]" />
        </span>
      </div>
  );
}

function RecommendationIntro({
  message,
  reasons,
}: {
  message: string;
  reasons: string[];
}) {
  const readableMessage = cleanupAiSpacing(message);

  return (
      <div className="mb-3 rounded-2xl bg-[#fffaf4] px-3 py-2.5">
        <div className="min-w-0">
          <p className="whitespace-pre-wrap text-sm leading-6 text-[#3d2b22]">
            {readableMessage}
          </p>
          {reasons.length > 0 && (
              <div className="mt-2 rounded-xl bg-white/75 px-3 py-2">
                <p className="text-xs font-extrabold text-[#6d5a50]">추천 이유</p>
                <ul className="mt-1 space-y-1 text-xs leading-5 text-[#6d5a50]">
                  {reasons.map((reason, index) => (
                      <li key={`${reason}-${index}`} className="flex gap-1.5">
                        <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-[#d84315]" />
                        <span>{reason}</span>
                      </li>
                  ))}
                </ul>
              </div>
          )}
        </div>
      </div>
  );
}

function RecommendedPostMiniCard({ post }: { post: RecommendedPost }) {
  return (
      <Link
          to={`/posts/${post.postId}`}
          className="group block rounded-xl border border-[#f0d8c8] bg-white px-4 py-3 text-[#3d2b22] transition-all hover:border-[#d84315] hover:bg-[#fff8f3] hover:shadow-sm"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-extrabold text-[#212121]">{post.placeName}</p>
          </div>
          <span className="shrink-0 rounded-full bg-[#e8f5e9] px-2.5 py-1 text-[11px] font-bold text-[#2e7d32]">
            모집중
          </span>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs font-semibold text-[#6d5a50]">
          <span className="flex items-center gap-1">
            <CalendarClock size={13} className="text-[#d84315]" />
            {formatMeetAt(post.meetAt)}
          </span>
          <span className="flex items-center gap-1">
            <Coins size={13} className="text-[#d84315]" />
            {post.deposit.toLocaleString()}P
          </span>
          <span className="ml-auto inline-flex items-center gap-1 text-[#d84315]">
            상세 보기
            <ArrowRight size={13} className="transition-transform group-hover:translate-x-0.5" />
          </span>
        </div>
      </Link>
  );
}

function ParsedRecommendationCard({ post }: { post: ParsedRecommendation }) {
  return (
      <Link
          to={`/posts/${post.postId}`}
          className="group block rounded-xl border border-[#f0d8c8] bg-white px-4 py-3 text-[#3d2b22] transition-all hover:border-[#d84315] hover:bg-[#fff8f3] hover:shadow-sm"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-extrabold text-[#212121]">
              {post.placeName || `게시글 #${post.postId}`}
            </p>
          </div>
          <span className="shrink-0 rounded-full bg-[#e8f5e9] px-2.5 py-1 text-[11px] font-bold text-[#2e7d32]">
            모집중
          </span>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs font-semibold text-[#6d5a50]">
          {post.meetAt && (
              <span className="flex items-center gap-1">
                <CalendarClock size={13} className="text-[#d84315]" />
                {post.meetAt}
              </span>
          )}
          {post.deposit && (
              <span className="flex items-center gap-1">
                <Coins size={13} className="text-[#d84315]" />
                {post.deposit}
              </span>
          )}
          <span className="ml-auto inline-flex items-center gap-1 text-[#d84315]">
            상세 보기
            <ArrowRight size={13} className="transition-transform group-hover:translate-x-0.5" />
          </span>
        </div>
      </Link>
  );
}

function getDisplayRecommendedPosts(message: ChatMessage) {
  const posts = message.recommendedPosts ?? [];

  if (posts.length === 0) {
    return [];
  }

  const answerText = normalizeForMatching(message.content);
  const mentionedPosts = posts.filter((post) => {
    const placeName = normalizeForMatching(post.placeName);

    return placeName.length > 0 && answerText.includes(placeName);
  });

  return mentionedPosts.length > 0 ? mentionedPosts : posts;
}

function getRecommendationReasons(
  recommendedPosts: RecommendedPost[],
  parsedRecommendations: ParsedRecommendation[]
) {
  const reasons = [
    ...recommendedPosts.map((post) => post.reason),
    ...parsedRecommendations.map((post) => post.reason),
  ]
      .map((reason) => cleanupRecommendationReason(reason))
      .filter((reason): reason is string => Boolean(reason));

  return reasons.slice(0, 3);
}

function cleanupRecommendationReason(reason?: string) {
  if (!reason) return '';

  const cleaned = reason
      .replace(/^이유\s*:\s*/i, '')
      .replace(/신청\s*가능합니다\.?$/g, '')
      .trim();

  return cleanupAiSpacing(cleaned);
}

function cleanupAiSpacing(text: string) {
  return text
      .replace(/요청하신조건/g, '요청하신 조건')
      .replace(/조건에맞는/g, '조건에 맞는')
      .replace(/모집글을(\d+)개/g, '모집글을 $1개')
      .replace(/(\d+)개찾았어요/g, '$1개 찾았어요')
      .replace(/중식메뉴/g, '중식 메뉴')
      .replace(/중국음식/g, '중국 음식')
      .replace(/가벼운식사/g, '가벼운 식사')
      .replace(/저녁시간대/g, '저녁 시간대')
      .replace(/점심시간대/g, '점심 시간대')
      .replace(/아침시간대/g, '아침 시간대')
      .replace(/빠르게식사/g, '빠르게 식사')
      .replace(/편한분위기/g, '편한 분위기')
      .replace(/책임비가낮/g, '책임비가 낮')
      .replace(/부담이적/g, '부담이 적')
      .replace(/찾는조건/g, '찾는 조건')
      .replace(/조건과맞아요/g, '조건과 맞아요')
      .replace(/시간대와가까워요/g, '시간대와 가까워요')
      .replace(/추천해요/g, '추천해요')
      .replace(/([.!?。])(?=\S)/g, '$1 ');
}

function parseRecommendationCards(content: string): ParsedRecommendation[] {
  const formatted = formatMatchingContent(content);
  const parts = formatted.split(/\n-\s*게시글\s*ID\s*:\s*/);

  if (parts.length <= 1) {
    return [];
  }

  return parts
      .slice(1)
      .flatMap((part): ParsedRecommendation[] => {
        const nextBlock = part.split(/\n-\s*게시글\s*ID\s*:\s*/)[0];
        const idMatch = nextBlock.match(/^(\d+)/);

        if (!idMatch) {
          return []; // null 대신 빈 배열 반환 → flatMap이 펼쳐서 제거
        }

        return [{
          postId: Number(idMatch[1]),
          placeName: extractField(nextBlock, '장소'),
          meetAt: extractField(nextBlock, '시간'),
          deposit: extractField(nextBlock, '책임비'),
          reason: extractField(nextBlock, '이유'),
        }];
      })
      .slice(0, 3);
}

function extractField(block: string, label: string) {
  const pattern = new RegExp(`${label}\\s*:\\s*([^\\n]+)`);
  const match = block.match(pattern);
  return match?.[1]?.trim();
}

function getRecommendationIntro(content: string) {
  const formatted = formatMatchingContent(content);
  const intro = formatted.split(/\n-\s*게시글\s*ID\s*:/)[0]
      .replace(/추천한?계시는? 참고하여 신청하세요\.?/g, '')
      .split(/\n추천\s*이유\s*\n?/)[0]
      .trim();

  return intro || '조건에 맞는 식사팟을 찾았어요.';
}

function getMentionedPostIds(content: string) {
  const ids = new Set<number>();
  const labeledIdPattern = /(?:게시글\s*(?:ID|아이디)?|글\s*ID)\s*[:#]?\s*(\d+)/gi;
  const idListPattern = /추천(?:한)?\s*게시글\s*(?:ID|아이디)[^\d[]*\[?([0-9,\s]+)\]?/gi;

  for (const match of content.matchAll(labeledIdPattern)) {
    ids.add(Number(match[1]));
  }

  for (const match of content.matchAll(idListPattern)) {
    match[1]
        .split(',')
        .map((value) => Number(value.trim()))
        .filter((value) => Number.isFinite(value) && value > 0)
        .forEach((value) => ids.add(value));
  }

  return Array.from(ids).slice(0, 3);
}

function formatMatchingContent(content: string) {
  return content
      .replace(/\*\*/g, '')
      .replace(/^#{1,6}\s*/gm, '')
      .replace(/recommendedPostIds\s*[:=]?\s*\[[\d,\s]*\]/gi, '')
      .replace(/추천(?:한)?\s*게시글\s*(?:ID|아이디)\s*[:=]?\s*\[[\d,\s]*\]/gi, '')
      .replace(/([^\n])-\s*게시글\s*ID/g, '$1\n- 게시글 ID')
      .replace(/([^\n])(\d+\.\s*)/g, '$1\n$2')
      .replace(/([^\n])(추천한?\s*게시글\s*(?:ID|아이디))/g, '$1\n\n$2')
      .replace(/([^\n])(조건에 맞는|추천 후보|신청 가능 여부)/g, '$1\n$2')
      .replace(/([^\n])(추천한?계시는?\s*참고)/g, '$1\n$2')
      .replace(/\s*\/\s*(장소|시간|책임비|이유|신청 가능 여부)\s*:/g, '\n  $1: ')
      .replace(/(신청\s*가능합니다\.?)/g, '$1\n')
      .replace(/\n{3,}/g, '\n\n')
      .trimStart();
}

function isLimitedRecommendation(message: ChatMessage) {
  if (message.fallbackUsed) return true;

  return ['없어요', '없습니다', '없으니', '조건을 넓혀', '시간대를 조금 넓혀'].some((keyword) =>
      message.content.includes(keyword)
  );
}

function hasRecommendedAnswer(message: ChatMessage) {
  if (message.fallbackUsed) return false;

  return ['찾았어요', '후보', '추천', '신청 가능'].some((keyword) => message.content.includes(keyword));
}

function isClarificationQuestion(content: string) {
  return ['알려주실래요', '넓혀볼까요', '뜻하시나요', '말씀해 주실 수 있나요', '?'].some((keyword) =>
      content.includes(keyword)
  );
}

function normalizeForMatching(value: string) {
  return value.replace(/\s/g, '').toLowerCase();
}

function RecommendedPostCard({ post }: { post: RecommendedPost }) {
  const canApply = post.applicationAvailable && post.pointAffordable;

  return (
      <Link
          to={`/posts/${post.postId}`}
          className="group block rounded-xl border border-[#e0e0e0] bg-[#fffdfb] p-3 transition-all hover:border-[#d84315] hover:bg-[#fff8f3] hover:shadow-md"
      >
        <div className="mb-2 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate text-sm font-bold text-[#212121]">{post.placeName}</h3>
            <p className="mt-1 line-clamp-2 text-xs leading-5 text-[#757575]">{post.reason}</p>
          </div>
          <span className="shrink-0 rounded-full bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#d84315]">
            {post.deposit.toLocaleString()}P
          </span>
        </div>

        <div className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-[#616161]">
          <span className="flex items-center gap-1">
            <CalendarClock size={14} className="text-[#d84315]" />
            {formatMeetAt(post.meetAt)}
          </span>
          <span className="flex items-center gap-1">
            <Coins size={14} className="text-[#d84315]" />
            책임비
          </span>
        </div>

        <div className="flex flex-wrap gap-1.5">
          <StatusPill
              active={post.applicationAvailable}
              activeLabel="신청 가능"
              inactiveLabel="신청 불가"
          />
          <StatusPill
              active={post.pointAffordable}
              activeLabel="포인트 충분"
              inactiveLabel="포인트 부족"
          />
          {!canApply && (
              <span className="rounded-full bg-[#ffebee] px-2.5 py-1 text-[11px] font-semibold text-[#c62828]">
                확인 필요
              </span>
          )}
        </div>

        <div className={`mt-3 inline-flex w-full items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-center text-xs font-bold transition-colors ${
            canApply ? 'bg-[#d84315] text-white group-hover:bg-[#bf360c]' : 'bg-[#f5f5f5] text-[#757575] group-hover:bg-[#eeeeee]'
        }`}>
          {canApply ? '게시글 확인하고 신청하기' : '게시글 상태 확인하기'}
          <ArrowRight size={13} />
        </div>
      </Link>
  );
}

function StatusPill({
  active,
  activeLabel,
  inactiveLabel,
}: {
  active: boolean;
  activeLabel: string;
  inactiveLabel: string;
}) {
  return (
      <span
          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-semibold ${
              active ? 'bg-[#e8f5e9] text-[#2e7d32]' : 'bg-[#f5f5f5] text-[#757575]'
          }`}
      >
        {active ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
        {active ? activeLabel : inactiveLabel}
      </span>
  );
}

function formatMeetAt(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
