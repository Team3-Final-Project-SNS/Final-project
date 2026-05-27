import { FormEvent, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, Bot, CalendarClock, CheckCircle2, Coins, Loader2, Send, Sparkles, XCircle } from 'lucide-react';
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
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-[#fff3e0] px-3 py-1 text-xs font-bold text-[#ef6c00]">
              <Sparkles size={14} />
              AI 매칭 추천
            </div>
            <h1 className="text-3xl font-bold text-[#212121]">식사팟 추천 챗봇</h1>
            <p className="mt-2 text-sm text-[#757575]">조건을 입력하면 참여 가능한 게시글을 후보 안에서 추천합니다.</p>
          </div>
          <Link
              to="/posts"
              className="inline-flex items-center justify-center rounded-lg border border-[#e0e0e0] bg-white px-4 py-2.5 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            게시글 보기
          </Link>
        </div>

        <div className="mb-4 rounded-2xl border border-[#e0e0e0] bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-[#424242]">
            <Bot size={18} className="text-[#d84315]" />
            추천 질문
          </div>
          <div className="flex flex-wrap gap-2">
            {EXAMPLE_QUESTIONS.map((question) => (
                <button
                    key={question}
                    type="button"
                    onClick={() => submitMessage(question)}
                    disabled={loading}
                    className="rounded-full border border-[#e0e0e0] bg-[#fafafa] px-3 py-2 text-xs font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:bg-[#fff3e0] hover:text-[#d84315] disabled:opacity-60"
                >
                  {question}
                </button>
            ))}
          </div>
        </div>

        {error && (
            <div className="mb-4 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
        )}

        <div className="rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          <div className="h-[560px] overflow-y-auto px-4 py-5 sm:px-6">
            <div className="space-y-5">
              {messages.map((message) => (
                  <ChatBubble key={message.id} message={message} />
              ))}
              {loading && (
                  <div className="flex justify-start">
                    <div className="inline-flex items-center gap-2 rounded-2xl bg-[#f5f5f5] px-4 py-3 text-sm font-semibold text-[#616161]">
                      <Loader2 size={16} className="animate-spin text-[#d84315]" />
                      추천을 찾는 중...
                    </div>
                  </div>
              )}
            </div>
          </div>

          <form onSubmit={handleSubmit} className="border-t border-[#eeeeee] p-4">
            <div className="flex gap-2">
              <input
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="예: 오늘 3시쯤 양식 먹을 사람 2명 정도 추천해줘"
                  className="min-w-0 flex-1 rounded-xl border border-[#e0e0e0] px-4 py-3 text-sm focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
              />
              <button
                  type="submit"
                  disabled={loading || input.trim().length === 0}
                  className="inline-flex w-12 shrink-0 items-center justify-center rounded-xl bg-[#d84315] text-white shadow-md transition-colors hover:bg-[#bf360c] disabled:bg-[#e0e0e0] disabled:shadow-none"
                  aria-label="메시지 전송"
                  title="메시지 전송"
              >
                {loading ? <Loader2 size={20} className="animate-spin" /> : <Send size={20} />}
              </button>
            </div>
          </form>
        </div>
      </div>
  );
}

function ChatBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';

  return (
      <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
        <div className={`max-w-[92%] ${isUser ? 'sm:max-w-[72%]' : 'w-full'}`}>
          <div
              className={`rounded-2xl px-4 py-3 text-sm leading-6 ${
                  isUser
                      ? 'bg-[#d84315] text-white'
                      : 'border border-[#eeeeee] bg-[#fafafa] text-[#424242]'
              }`}
          >
            {!isUser && (
                <div className="mb-2 flex items-center gap-2 text-xs font-bold text-[#d84315]">
                  <Bot size={15} />
                  한끼 AI
                </div>
            )}
            <p className="whitespace-pre-wrap">{message.content}</p>
            {message.fallbackUsed && (
                <div className="mt-3 rounded-lg border border-[#ffcc80] bg-[#fff8e1] px-3 py-2 text-xs font-semibold text-[#ef6c00]">
                  AI 추천이 일부 제한된 상태입니다.
                </div>
            )}
          </div>

          {!isUser && message.recommendedPosts && message.recommendedPosts.length > 0 && (
              <div className="mt-3 rounded-2xl border border-[#eeeeee] bg-white p-3">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-bold text-[#212121]">
                      {isLimitedRecommendation(message) ? '조건을 넓혀볼 만한 후보' : '추천 게시글'}
                    </p>
                    <p className="mt-0.5 text-xs text-[#9e9e9e]">AI가 최대 5개의 후보 게시글을 비교했어요.</p>
                  </div>
                  <span className="rounded-full bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#d84315]">
                    {message.recommendedPosts.length}개
                  </span>
                </div>

                <div className="grid gap-2 md:grid-cols-2">
                  {message.recommendedPosts.map((post) => (
                      <RecommendedPostCard key={post.postId} post={post} />
                  ))}
                </div>
              </div>
          )}
        </div>
      </div>
  );
}

function isLimitedRecommendation(message: ChatMessage) {
  if (message.fallbackUsed) return true;

  return ['없어요', '없습니다', '없으니', '조건을 넓혀', '시간대를 조금 넓혀'].some((keyword) =>
      message.content.includes(keyword)
  );
}

function RecommendedPostCard({ post }: { post: RecommendedPost }) {
  const canApply = post.applicationAvailable && post.pointAffordable;

  return (
      <Link
          to={`/posts/${post.postId}`}
          className="block rounded-xl border border-[#e0e0e0] bg-[#fffdfb] p-3 transition-all hover:border-[#d84315] hover:shadow-md"
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

        <div className={`mt-3 rounded-lg px-3 py-2 text-center text-xs font-bold ${
            canApply ? 'bg-[#d84315] text-white' : 'bg-[#f5f5f5] text-[#757575]'
        }`}>
          {canApply ? '게시글 확인하고 신청하기' : '게시글 상태 확인하기'}
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
