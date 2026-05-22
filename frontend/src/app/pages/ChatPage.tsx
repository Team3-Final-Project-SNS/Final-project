import { useState, useEffect, useRef } from 'react';
import { useParams, Link, useLocation } from 'react-router';
import { ArrowLeft, Send, MapPin, Loader2, AlertCircle } from 'lucide-react';
import { getChatMessages, ChatMessageResponse, getChatRooms } from '../../api/chatApi';
import { getMatchDetail, GetMatchResponse } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

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

        if (!currentMatchId && currentChatRoomId) {
          const roomsRes = await getChatRooms();
          const room = roomsRes.data.data.find((item) => item.chatRoomId === currentChatRoomId);
          currentMatchId = room?.matchId ?? null;
        }

        if (!currentMatchId) {
          throw new Error('MATCH_NOT_FOUND');
        }

        const [matchRes, userRes] = await Promise.all([
          getMatchDetail(currentMatchId),
          getUserMe(),
        ]);
        currentChatRoomId = currentChatRoomId ?? matchRes.data.data.chatRoomId;

        if (!currentChatRoomId) {
          throw new Error('CHAT_ROOM_NOT_FOUND');
        }

        const historyRes = await getChatMessages(currentChatRoomId);

        setMessages(toChronologicalMessages(historyRes.data.data.content));
        setCursor(historyRes.data.data.nextCursor);
        setHasNext(historyRes.data.data.hasNext);
        setMatchInfo(matchRes.data.data);
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

    const accessToken = sessionStorage.getItem("accessToken");
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
        client.subscribe(`/sub/chat/rooms/${chatRoomId}`, (payload) => {
          const newMessage: ChatMessageResponse = JSON.parse(payload.body);
          setMessages((prev) => [
            ...prev,
            newMessage.senderId === currentUserId ? newMessage : { ...newMessage, isRead: true },
          ]);
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
                <Link
                    to={`/matches/${matchInfo.matchId}/place-verification`}
                    className="px-5 py-2.5 bg-[#d84315] text-white rounded-xl text-sm font-semibold hover:bg-[#bf360c] transition-all shadow-md flex items-center gap-2"
                >
                  <MapPin size={16} />
                  장소 인증
                </Link>
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

        {error && (
            <div className="bg-[#ffebee] border-x border-b border-[#ef5350] px-4 py-3 flex items-start gap-2 text-sm text-[#c62828]">
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
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

              // 날짜 구분선 표시 여부
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
          {/* 전송 버튼: 연결 안됐거나 메시지 없으면 비활성화 */}
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
