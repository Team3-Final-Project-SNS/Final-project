import { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router';
import { ArrowLeft, Send, MapPin, Loader2, AlertCircle } from 'lucide-react';
import { getChatMessages, ChatMessageResponse, leaveChatRoom } from '../../api/chatApi';
import { getMatchDetail, GetMatchResponse } from '../../api/matchApi';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export default function ChatPage() {
  const { roomId } = useParams();
  const chatRoomId = Number(roomId);
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [matchInfo, setMatchInfo] = useState<GetMatchResponse | null>(null);
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
      try {
        const [historyRes, matchRes] = await Promise.all([
          getChatMessages(chatRoomId),
          getMatchDetail(chatRoomId)
        ]);

        setMessages(historyRes.data.data.messages);
        setCursor(historyRes.data.data.nextCursorId);
        setHasNext(historyRes.data.data.hasNext);
        setMatchInfo(matchRes.data.data);
      } catch (err: any) {
        setError('채팅 정보를 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [chatRoomId]);

  // 웹소켓 연결
  useEffect(() => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
    const socket = new SockJS(`${baseUrl}/ws/chat`);
    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log(str),
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/sub/chat/rooms/${chatRoomId}`, (payload) => {
          const newMessage = JSON.parse(payload.body);
          setMessages((prev) => [...prev, newMessage]);
        });
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
      },
    });

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, [chatRoomId]);

  // 자동 스크롤
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim() || !stompClient.current?.connected) return;

    stompClient.current.publish({
      destination: `/pub/chat/rooms/${chatRoomId}`,
      body: JSON.stringify({ content: message }),
    });

    setMessage('');
  };

  const loadMore = async () => {
    if (!hasNext || !cursor) return;
    try {
      const res = await getChatMessages(chatRoomId, cursor);
      const olderMessages = res.data.data.messages;
      setMessages((prev) => [...olderMessages, ...prev]);
      setCursor(res.data.data.nextCursorId);
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
              <h2 className="font-semibold text-[#212121]">{matchInfo?.postTitle || '채팅'}</h2>
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
            <Link
                to={`/matches/${chatRoomId}/place-verification`}
                className="px-5 py-2.5 bg-[#d84315] text-white rounded-xl text-sm font-semibold hover:bg-[#bf360c] transition-all shadow-md flex items-center gap-2"
            >
              <MapPin size={16} />
              장소 인증
            </Link>
          </div>
        </div>

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
              // 내 메시지 여부 판단: 상대방 닉네임이 아닌 경우 = 내 메시지
              const isMe = msg.senderNickname !== matchInfo?.opponentNickname;

              // 날짜 구분선 표시 여부
              const date = new Date(msg.createdAt).toLocaleDateString();
              const showDate = idx === 0 || new Date(messages[idx - 1].createdAt).toLocaleDateString() !== date;

              return (
                  <div key={msg.messageId}>
                    {/* 날짜 구분선 */}
                    {showDate && (
                        <div className="text-center text-xs text-[#9e9e9e] my-4">{date}</div>
                    )}

                    {/* 말풍선: 내 메시지는 오른쪽, 상대 메시지는 왼쪽 */}
                    <div className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[70%] flex flex-col ${isMe ? 'items-end' : 'items-start'}`}>
                        {/* 상대방 닉네임 (상대 메시지에만 표시) */}
                        {!isMe && (
                            <span className="text-[10px] text-[#9e9e9e] mb-1 ml-1">{msg.senderNickname}</span>
                        )}
                        {/* 메시지 말풍선 */}
                        <div
                            className={`px-4 py-2.5 rounded-lg ${
                                isMe ? 'bg-[#d84315] text-white' : 'bg-[#f5f5f5] text-[#212121]'
                            }`}
                        >
                          <p className="text-sm">{msg.content}</p>
                        </div>
                        {/* 전송 시간 */}
                        <span className="text-[10px] text-[#9e9e9e] mt-1">
                      {new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                      </div>
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