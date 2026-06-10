import { useState, useEffect, useRef } from 'react';
import { useParams, Link, useLocation } from 'react-router';
import { ArrowLeft, Send, MapPin, Loader2, AlertCircle, Clock, Check } from 'lucide-react';
import { getChatMessages, ChatMessageResponse } from '@/api/chatApi';
import { getMatchDetail, GetMatchResponse, getMyMatches } from '@/api/matchApi';
import {
  createMeetExtension,
  getMeetExtension,
  acceptMeetExtension,
  rejectMeetExtension,
  MeetExtensionResponse,
} from '@/api/meetApi';
import { getUserMe } from '@/api/userApi';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '@/api/axiosInstance';

const formatMessageDate = (value: string) =>
    new Date(value).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
    });

const formatMessageTime = (value: string) =>
    new Date(value).toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });

const toChronologicalMessages = (messages: ChatMessageResponse[]) =>
    [...messages].reverse();

const mergeMessages = (
    currentMessages: ChatMessageResponse[],
    latestMessages: ChatMessageResponse[],
) => {
  const messageMap = new Map<number, ChatMessageResponse>();

  currentMessages.forEach((item) => messageMap.set(item.messageId, item));
  latestMessages.forEach((item) => messageMap.set(item.messageId, item));

  return [...messageMap.values()].sort((a, b) => a.messageId - b.messageId);
};

const resolveMatchIdByChatRoomId = async (chatRoomId: number) => {
  let page = 0;
  const size = 100;

  while (page < 5) {
    const response = await getMyMatches(undefined, page, size);
    const data = response.data.data;
    const matchedRoom = data.content
        .filter((item) => item.chatRoomId === chatRoomId)
        .sort((a, b) => {
          if (a.status === 'MATCHED' && b.status !== 'MATCHED') return -1;
          if (a.status !== 'MATCHED' && b.status === 'MATCHED') return 1;
          return new Date(b.matchedAt).getTime() - new Date(a.matchedAt).getTime();
        })[0];

    if (matchedRoom) {
      return matchedRoom.matchId;
    }

    if (!data.hasNext) {
      return null;
    }

    page += 1;
  }

  return null;
};

export default function ChatPage() {
  const { roomId, id } = useParams();
  const location = useLocation();
  const routeChatRoomId = roomId ? Number(roomId) : null;
  const routeMatchId = id ? Number(id) : null;
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [matchInfo, setMatchInfo] = useState<GetMatchResponse | null>(null);
  const [chatRoomId, setChatRoomId] = useState<number | null>(routeChatRoomId);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cursor, setCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [connected, setConnected] = useState(false);
  const [extensionLoading, setExtensionLoading] = useState(false);
  const [extensionInfo, setExtensionInfo] = useState<MeetExtensionResponse | null>(null);
  const [extensionActionLoading, setExtensionActionLoading] = useState(false);

  const stompClient = useRef<Client | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  // 초기 데이터 로드 (매칭 정보 + 메시지 히스토리)
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');
      try {
        let currentMatchId: number | null = location.state?.matchId ?? routeMatchId;
        let currentChatRoomId: number | null = routeChatRoomId;
        let nextMatchInfo: GetMatchResponse | null = null;

        if (!currentMatchId && currentChatRoomId) {
          try {
            currentMatchId = await resolveMatchIdByChatRoomId(currentChatRoomId);
          } catch (roomLookupError) {
            console.error('Failed to resolve match from chat room', roomLookupError);
          }
        }

        if (currentMatchId) {
          try {
            const matchRes = await getMatchDetail(currentMatchId);
            nextMatchInfo = matchRes.data.data;
            currentChatRoomId = currentChatRoomId ?? nextMatchInfo.chatRoomId;
          } catch (matchLookupError) {
            if (!currentChatRoomId) {
              throw matchLookupError;
            }
            console.error('Failed to load match detail for chat room', matchLookupError);
          }
        }

        if (!currentChatRoomId) {
          throw new Error('CHAT_ROOM_NOT_FOUND');
        }

        const [historyRes, userRes] = await Promise.all([
          getChatMessages(currentChatRoomId),
          getUserMe(),
        ]);

        setMessages(toChronologicalMessages(historyRes.data.data.content));
        setCursor(historyRes.data.data.nextCursor);
        setHasNext(historyRes.data.data.hasNext);
        setMatchInfo(nextMatchInfo);
        setChatRoomId(currentChatRoomId);
        setCurrentUserId(userRes.data.data.userId);
      } catch (err: any) {
        setError('채팅 정보를 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [routeChatRoomId, routeMatchId, location.state?.matchId]);

  // 웹소켓 연결
  useEffect(() => {
    if (!chatRoomId) return;

    const accessToken = getAccessToken();
    if (!accessToken) {
      setConnected(false);
      setError('로그인이 필요합니다. 다시 로그인해주세요.');
      return;
    }

    const baseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
    const socket = new SockJS(`${baseUrl}/ws/chat?token=${encodeURIComponent(accessToken)}`);
    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log(str),
      onConnect: () => {
        setConnected(true);
        setError('');
        client.subscribe(`/user/sub/chat/rooms/${chatRoomId}`, (payload) => {
          const newMessage: ChatMessageResponse = JSON.parse(payload.body);
          setMessages((prev) => [
            ...prev,
            newMessage.senderId === currentUserId ? newMessage : { ...newMessage, isRead: true },
          ]);
        });
        client.subscribe('/user/queue/errors', (payload) => {
          setError(payload.body);
        });
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        setConnected(false);
        setError('채팅 연결 중 오류가 발생했습니다.');
      },
      onWebSocketClose: () => {
        setConnected(false);
      },
    });

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, [chatRoomId, currentUserId]);

  useEffect(() => {
    if (!chatRoomId || currentUserId === null) return;

    const syncReadStatus = async () => {
      try {
        const res = await getChatMessages(chatRoomId);
        setMessages((prev) => mergeMessages(prev, toChronologicalMessages(res.data.data.content)));
      } catch (err) {
        console.error('Failed to sync read status', err);
      }
    };

    const intervalId = window.setInterval(syncReadStatus, 5000);
    return () => window.clearInterval(intervalId);
  }, [chatRoomId, currentUserId]);

  // 연장 상태 폴링 — MATCHED 상태일 때만 3초마다 조회
  useEffect(() => {
    if (!matchInfo || matchInfo.status !== 'MATCHED') return;

    const matchId = matchInfo.matchId;

    const fetchExtension = async () => {
      try {
        const res = await getMeetExtension(matchId);
        setExtensionInfo(res.data.data);
      } catch {
        // 폴링 실패는 무시
      }
    };

    fetchExtension();
    const intervalId = setInterval(fetchExtension, 3000);
    return () => clearInterval(intervalId);
  }, [matchInfo]);

  // 자동 스크롤
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim() || !chatRoomId || !stompClient.current?.connected) return;

    stompClient.current.publish({
      destination: `/pub/chat/rooms/${chatRoomId}`,
      body: JSON.stringify({ content: message }),
    });

    setMessage('');
  };

  const handleExtendMeetTime = async () => {
    if (!matchInfo || extensionLoading) return;

    try {
      setExtensionLoading(true);
      const res = await createMeetExtension(matchInfo.matchId);
      // 요청 성공 시 즉시 REQUESTED 상태로 UI 반영 (폴링 전 공백 방지)
      setExtensionInfo({ ...res.data.data, isMyRequest: true });
    } catch (err: any) {
      alert(err.response?.data?.message || '시간 연장 요청에 실패했습니다.');
    } finally {
      setExtensionLoading(false);
    }
  };

  const handleAcceptExtension = async () => {
    if (!matchInfo || extensionActionLoading) return;

    try {
      setExtensionActionLoading(true);
      await acceptMeetExtension(matchInfo.matchId);
      setExtensionInfo((prev) => prev ? { ...prev, extensionStatus: 'ACCEPTED' } : prev);
    } catch (err: any) {
      alert(err.response?.data?.message || '수락에 실패했습니다.');
    } finally {
      setExtensionActionLoading(false);
    }
  };

  const handleRejectExtension = async () => {
    if (!matchInfo || extensionActionLoading) return;

    try {
      setExtensionActionLoading(true);
      await rejectMeetExtension(matchInfo.matchId);
      setExtensionInfo((prev) => prev ? { ...prev, extensionStatus: 'REJECTED' } : prev);
    } catch (err: any) {
      alert(err.response?.data?.message || '거절에 실패했습니다.');
    } finally {
      setExtensionActionLoading(false);
    }
  };

  const loadMore = async () => {
    if (!hasNext || !cursor || !chatRoomId) return;
    try {
      const res = await getChatMessages(chatRoomId, cursor);
      const olderMessages = toChronologicalMessages(res.data.data.content);
      setMessages((prev) => [...olderMessages, ...prev]);
      setCursor(res.data.data.nextCursor);
      setHasNext(res.data.data.hasNext);
    } catch (err) {
      console.error('Failed to load older messages', err);
    }
  };

  // 시간 연장 버튼 활성화 조건: NONE 또는 EXPIRED 상태일 때만 요청 가능
  const canRequestExtension =
      !extensionInfo ||
      extensionInfo.extensionStatus === 'NONE' ||
      extensionInfo.extensionStatus === 'EXPIRED';

  // 신청자 여부: 현재 로그인한 유저가 applicant일 때만 시간 연장 버튼 노출
  const isApplicant = currentUserId !== null && matchInfo !== null && currentUserId === matchInfo.applicantId;

  if (loading) return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="animate-spin text-[#d84315] mb-4" size={40} />
        <p className="text-[#616161]">채팅방에 입장하는 중...</p>
      </div>
  );

  return (
      <div className="max-w-3xl mx-auto h-[calc(100vh-12rem)] flex flex-col">
        {/* 채팅방 헤더 */}
        <div className="bg-white border border-[#e0e0e0] rounded-t-2xl p-5 flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-3">
            <Link to="/matches" className="text-[#616161] hover:text-[#d84315]">
              <ArrowLeft size={20} />
            </Link>
            <div>
              <h2 className="font-semibold text-[#212121]">{matchInfo ? `${matchInfo.placeName} 만남` : '채팅'}</h2>
              <p className="text-xs text-[#9e9e9e]">
                {matchInfo?.placeName} · {matchInfo?.meetAt ? new Date(matchInfo.meetAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
              </p>
            </div>
          </div>

          <div className="flex gap-2">
            {/* 웹소켓 연결 상태 표시 */}
            {!connected && (
                <span className="text-xs text-red-500 flex items-center gap-1">
              <AlertCircle size={12} /> 연결 끊김
            </span>
            )}
            {/* 장소 인증 버튼 */}
            {matchInfo ? (
                <>
                  <Link
                      to={`/matches/${matchInfo.matchId}/place-verification`}
                      className="px-5 py-2.5 bg-[#d84315] text-white rounded-xl text-sm font-semibold hover:bg-[#bf360c] transition-all shadow-md flex items-center gap-2"
                  >
                    <MapPin size={16} />
                    장소 인증
                  </Link>
                  {isApplicant && (
                    <button
                        type="button"
                        onClick={handleExtendMeetTime}
                        disabled={extensionLoading || !canRequestExtension}
                        className="px-5 py-2.5 bg-white border border-[#d84315] text-[#d84315] rounded-xl text-sm font-semibold hover:bg-[#fff3e0] transition-all shadow-sm flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {extensionLoading ? <Loader2 size={16} className="animate-spin" /> : <Clock size={16} />}
                      시간 연장
                    </button>
                  )}
                </>
            ) : (
                <button
                    type="button"
                    disabled
                    className="px-5 py-2.5 bg-[#e0e0e0] text-white rounded-xl text-sm font-semibold shadow-md flex items-center gap-2"
                >
                  <MapPin size={16} />
                  장소 인증
                </button>
            )}
          </div>
        </div>

        {/* 에러 배너 */}
        {error && (
            <div className="bg-[#ffebee] border-x border-b border-[#ef5350] px-4 py-3 flex items-start gap-2 text-sm text-[#c62828]">
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
        )}

        {/* 연장 상태 배너 */}
        {extensionInfo && extensionInfo.extensionStatus !== 'NONE' && matchInfo?.status === 'MATCHED' && (
            <>
              {/* 상대방이 요청 → 수락/거절 배너 */}
              {extensionInfo.extensionStatus === 'REQUESTED' && !extensionInfo.isMyRequest && (
                  <div className="bg-[#fff3e0] border-x border-b border-[#ff9800] px-4 py-3 flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm text-[#e65100]">
                      <Clock size={16} className="shrink-0" />
                      <span>
                        <strong>{extensionInfo.requesterNickname}</strong>님이 만남 시간 10분 연장을 요청했습니다.
                      </span>
                    </div>
                    <div className="flex gap-2 shrink-0">
                      <button
                          onClick={handleAcceptExtension}
                          disabled={extensionActionLoading}
                          className="px-3 py-1.5 bg-[#4caf50] text-white text-xs font-semibold rounded-lg hover:bg-[#43a047] transition-colors disabled:opacity-50"
                      >
                        {extensionActionLoading ? <Loader2 size={12} className="animate-spin" /> : '수락'}
                      </button>
                      <button
                          onClick={handleRejectExtension}
                          disabled={extensionActionLoading}
                          className="px-3 py-1.5 bg-white border border-[#ef5350] text-[#ef5350] text-xs font-semibold rounded-lg hover:bg-[#ffebee] transition-colors disabled:opacity-50"
                      >
                        거절
                      </button>
                    </div>
                  </div>
              )}

              {/* 내가 요청 → 대기 중 배너 */}
              {extensionInfo.extensionStatus === 'REQUESTED' && extensionInfo.isMyRequest && (
                  <div className="bg-[#e3f2fd] border-x border-b border-[#2196f3] px-4 py-3 flex items-center gap-2 text-sm text-[#1565c0]">
                    <Clock size={16} className="shrink-0" />
                    <span>연장 요청을 보냈습니다. 상대방의 응답을 기다리는 중...</span>
                  </div>
              )}

              {/* 수락됨 → 양측에 새 약속시간 표시 */}
              {extensionInfo.extensionStatus === 'ACCEPTED' && (
                  <div className="bg-[#e8f5e9] border-x border-b border-[#4caf50] px-4 py-3 flex items-center gap-2 text-sm text-[#2e7d32]">
                    <Check size={16} className="shrink-0" />
                    <span>
                      만남 시간이 10분 연장되었습니다. 새 약속시간:{' '}
                      <strong>
                        {new Date(extensionInfo.expectedMeetAt).toLocaleTimeString('ko-KR', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </strong>
                    </span>
                  </div>
              )}

              {/* 거절됨 → 요청자에게만 표시 */}
              {extensionInfo.extensionStatus === 'REJECTED' && extensionInfo.isMyRequest && (
                  <div className="bg-[#ffebee] border-x border-b border-[#ef5350] px-4 py-3 flex items-center gap-2 text-sm text-[#c62828]">
                    <AlertCircle size={16} className="shrink-0" />
                    <span>상대방이 시간 연장 요청을 거절했습니다.</span>
                  </div>
              )}

              {/* 만료됨 → 요청자에게만 표시 */}
              {extensionInfo.extensionStatus === 'EXPIRED' && extensionInfo.isMyRequest && (
                  <div className="bg-[#f5f5f5] border-x border-b border-[#bdbdbd] px-4 py-3 flex items-center gap-2 text-sm text-[#757575]">
                    <AlertCircle size={16} className="shrink-0" />
                    <span>연장 요청이 5분 타임아웃으로 만료되었습니다. 다시 요청할 수 있습니다.</span>
                  </div>
              )}
            </>
        )}

        {/* 메시지 목록 영역 */}
        <div
            className="flex-1 bg-white border-x border-[#e0e0e0] p-4 overflow-y-auto"
            ref={scrollRef}
        >
          <div className="space-y-4">
            {/* 이전 메시지 더 불러오기 버튼 */}
            {hasNext && (
                <button
                    onClick={loadMore}
                    className="w-full py-2 text-xs text-[#9e9e9e] hover:text-[#d84315] transition-colors"
                >
                  이전 메시지 불러오기
                </button>
            )}

            {messages.map((msg, idx) => {
              const isMe = currentUserId !== null && msg.senderId === currentUserId;

              const date = formatMessageDate(msg.createdAt);
              const showDate = idx === 0 || formatMessageDate(messages[idx - 1].createdAt) !== date;

              return (
                  <div key={msg.messageId} className="space-y-2">
                    {/* 날짜 구분선 */}
                    {showDate && (
                        <div className="text-center text-xs font-semibold text-[#9e9e9e] my-5">{date}</div>
                    )}

                    <div className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}>
                      {!isMe ? (
                          <div className="max-w-[78%]">
                            <div className="mb-1 ml-1 text-xs font-semibold text-[#616161]">
                              {msg.senderNickname}
                            </div>
                            <div className="flex items-end gap-2">
                              <div className="rounded-2xl rounded-tl-sm bg-[#f5f5f5] px-4 py-2.5 text-[#212121] shadow-sm">
                                <p className="break-words text-sm leading-relaxed">{msg.content}</p>
                              </div>
                              <span className="shrink-0 text-[11px] text-[#9e9e9e]">
                                {formatMessageTime(msg.createdAt)}
                              </span>
                            </div>
                          </div>
                      ) : (
                          <div className="flex max-w-[78%] items-end justify-end gap-2">
                            <div className="flex shrink-0 flex-col items-end gap-0.5 text-[11px]">
                              <span className={msg.isRead ? 'text-[#bdbdbd]' : 'font-semibold text-[#d84315]'}>
                                {msg.isRead ? '읽음' : '안읽음'}
                              </span>
                              <span className="text-[#9e9e9e]">
                                {formatMessageTime(msg.createdAt)}
                              </span>
                            </div>
                            <div className="rounded-2xl rounded-tr-sm bg-[#d84315] px-4 py-2.5 text-white shadow-sm">
                              <p className="break-words text-sm leading-relaxed">{msg.content}</p>
                            </div>
                          </div>
                      )}
                    </div>
                  </div>
              );
            })}
          </div>
        </div>

        {/* 메시지 입력 폼 */}
        <form
            onSubmit={handleSend}
            className="bg-white border border-[#e0e0e0] rounded-b-2xl p-5 flex items-center gap-3 shadow-sm"
        >
          <input
              type="text"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={connected ? "메시지를 입력하세요..." : "연결 중입니다..."}
              disabled={!connected}
              className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
          />
          <button
              type="submit"
              disabled={!connected || !message.trim()}
              className="px-6 py-3 bg-[#d84315] text-white rounded-xl font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg flex items-center gap-2 disabled:bg-[#e0e0e0]"
          >
            <Send size={18} />
            전송
          </button>
        </form>
      </div>
  );
}