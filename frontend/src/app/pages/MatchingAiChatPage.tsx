import { FormEvent, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowRight, Bot, CalendarClock, CheckCircle2, Coins, Loader2, Send, Sparkles, XCircle } from 'lucide-react';
import { RecommendedPost, requestMatchingChat } from '../../api/aiApi';

const EXAMPLE_QUESTIONS = [
  '오늘 3시쯤 밥 먹을 사람 추천해줘',
  '양식 먹을 사람 2명 정도 추천해줘',
  '중식 먹을 사람 있어?',
  '빠르게 밥 먹고 헤어질 사람 추천해줘',
  '조용하게 저녁 먹을 사람 있어?',
  '책임비 낮은 식사팟 추천해줘',
];

type ChatMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  fallbackUsed?: boolean;
  recommendedPosts?: RecommendedPost[];
};

export default function MatchingAiChatPage() {
  const [conversationId, setConversationId] = useState<string | null>(null);
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

    try {
      const res = await requestMatchingChat({
        conversationId,
        message: trimmed,
      });
      const data = res.data.data;

      setConversationId(data.conversationId ?? conversationId);
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'assistant',
          content: data.answer,
          fallbackUsed: data.fallbackUsed,
          recommendedPosts: data.recommendedPosts ?? [],
        },
      ]);
    } catch (err: any) {
      console.error('AI matching chat failed', err);
      setError(err.response?.data?.message || '추천을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'assistant',
          content: '지금은 추천 결과를 가져오지 못했어요. 조건을 조금 바꾸거나 잠시 후 다시 요청해주세요.',
          recommendedPosts: [],
        },
      ]);
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

            <div className="h-[520px] overflow-y-auto pr-1">
              <div className="space-y-4">
                {messages.map((message) => (
                  <ChatBubble key={message.id} message={message} />
                ))}
                {loading && (
                  <div className="flex justify-start">
                    <div className="inline-flex items-center gap-2 rounded-[22px] bg-[#fff3e0] px-5 py-3.5 text-base font-semibold text-[#3d2b22]">
                      <Loader2 size={18} className="animate-spin text-[#d84315]" />
                      추천을 찾는 중...
                    </div>
                  </div>
                )}
              </div>
            </div>

            <form onSubmit={handleSubmit} className="mt-5 flex gap-3">
              <input
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="예: 오늘 3시쯤 양식 먹을 사람 2명 정도 추천해줘"
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
            <p className="whitespace-pre-wrap">{message.content}</p>
            {message.fallbackUsed && (
                <div className="mt-3 rounded-lg border border-[#ffcc80] bg-[#fff8e1] px-3 py-2 text-xs font-semibold text-[#ef6c00]">
                  AI 추천이 일부 제한된 상태입니다.
                </div>
            )}

            {!isUser && displayRecommendedPosts.length > 0 && (
                <div className="mt-4 border-t border-[#eeeeee] pt-3">
                  <p className="mb-2 text-xs font-bold text-[#616161]">추천 게시글 바로가기</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    {displayRecommendedPosts.map((post) => (
                        <Link
                            key={post.postId}
                            to={`/posts/${post.postId}`}
                            className="group flex min-w-0 items-center justify-between gap-2 rounded-xl border border-[#e0e0e0] bg-white px-3 py-2 text-xs font-bold text-[#424242] transition-all hover:border-[#d84315] hover:bg-[#fff8f3] hover:text-[#d84315]"
                        >
                          <span className="min-w-0 truncate">
                            {post.placeName} · {post.deposit.toLocaleString()}P
                          </span>
                          <ArrowRight size={13} className="shrink-0 transition-transform group-hover:translate-x-0.5" />
                        </Link>
                    ))}
                  </div>
                </div>
            )}
          </div>

          {!isUser && displayRecommendedPosts.length === 0 && hasRecommendedAnswer(message) && (
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
