import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { AlertCircle, ArrowLeft, Check, Clock, Loader2, MapPin, Send, Users, X } from 'lucide-react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ChatMemberResponse, ChatMessageResponse, getChatMembers, getChatMessages } from '@/api/chatApi';
import { getMatchDetail, GetMatchResponse, getMyMatches } from '@/api/matchApi';
import { acceptMeetExtension, createMeetExtension, getMeetExtension, MeetExtensionResponse, rejectMeetExtension } from '@/api/meetApi';
import { getUserMe } from '@/api/userApi';
import { getAccessToken } from '@/api/axiosInstance';
import { setExtendedMeetAt } from '@/store/matchStore';

const toChronologicalMessages = (messages: ChatMessageResponse[]) => [...messages].reverse();

const mergeMessages = (currentMessages: ChatMessageResponse[], latestMessages: ChatMessageResponse[]) => {
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
      .sort((a, b) => new Date(b.matchedAt).getTime() - new Date(a.matchedAt).getTime())[0];

    if (matchedRoom) return matchedRoom.matchId;
    if (!data.hasNext) return null;
    page += 1;
  }

  return null;
};

export default function ChatPage() {
  const { roomId, id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
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
  const [members, setMembers] = useState<ChatMemberResponse[]>([]);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersLoading, setMembersLoading] = useState(false);
  const [isReadOnlyChat, setIsReadOnlyChat] = useState(false);

  const stompClient = useRef<Client | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const isCancelledMatch = matchInfo?.status === 'CANCELLED';
  const isChatWritable = connected && !isReadOnlyChat && !isCancelledMatch && (!matchInfo || matchInfo.status === 'MATCHED');

  const blockCancelledChatAccess = () => {
    alert('매칭 취소자는 채팅방에 접근할 수 없습니다.');
    navigate('/matches', { replace: true });
  };

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');
      try {
        let currentMatchId: number | null = location.state?.matchId ?? routeMatchId;
        let currentChatRoomId: number | null = routeChatRoomId;
        let nextMatchInfo: GetMatchResponse | null = null;

        if (!currentMatchId && currentChatRoomId) {
          currentMatchId = await resolveMatchIdByChatRoomId(currentChatRoomId);
        }

        if (currentMatchId) {
          const matchRes = await getMatchDetail(currentMatchId);
          nextMatchInfo = matchRes.data.data;
          currentChatRoomId = currentChatRoomId ?? nextMatchInfo.chatRoomId;
        }

        if (!currentChatRoomId) throw new Error('CHAT_ROOM_NOT_FOUND');

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
        setIsReadOnlyChat(Boolean(nextMatchInfo && nextMatchInfo.status !== 'MATCHED'));
      } catch (err: any) {
        console.error(err);
        const code = err.response?.data?.code;
        if (code === 'CHAT_002' || code === 'CHAT_004' || code === 'MATCH_002') {
          blockCancelledChatAccess();
          return;
        }

        if (code === 'CHAT_003') {
          setIsReadOnlyChat(true);
        }

        setError('채팅방 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [routeChatRoomId, routeMatchId, location.state?.matchId]);

  useEffect(() => {
    if (!chatRoomId || isCancelledMatch) {
      setConnected(false);
      return;
    }

    const accessToken = getAccessToken();
    if (!accessToken) {
      setConnected(false);
      setError('로그인이 필요합니다. 다시 로그인해주세요.');
      return;
    }

    const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
    const socket = new SockJS(`${baseUrl}/ws/chat?token=${encodeURIComponent(accessToken)}`);
    const client = new Client({
      webSocketFactory: () => socket,
      debug: (value) => console.log(value),
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
          try {
            const parsedError = JSON.parse(payload.body);
            if (parsedError.code === 'CHAT_003') {
              setIsReadOnlyChat(true);
              setError('조회만 가능한 채팅방입니다. 메시지를 보낼 수 없습니다.');
              return;
            }
            if (parsedError.code === 'CHAT_004') {
              blockCancelledChatAccess();
              return;
            }
            setError(parsedError.message || payload.body);
          } catch {
            setError(payload.body);
          }
        });
      },
      onStompError: () => {
        setConnected(false);
        setError('채팅 연결 중 오류가 발생했습니다.');
      },
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    stompClient.current = client;

    return () => {
      client.deactivate();
    };
  }, [chatRoomId, currentUserId, isCancelledMatch]);

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

  useEffect(() => {
    if (!matchInfo || matchInfo.status !== 'MATCHED') return;

    const fetchExtension = async () => {
      try {
        const res = await getMeetExtension(matchInfo.matchId);
        const nextExtensionInfo = res.data.data;
        setExtensionInfo(nextExtensionInfo);
        if (nextExtensionInfo.extensionStatus === 'ACCEPTED' && nextExtensionInfo.expectedMeetAt) {
          setExtendedMeetAt(matchInfo.matchId, nextExtensionInfo.expectedMeetAt);
          setMatchInfo((prev) => prev ? { ...prev, meetAt: nextExtensionInfo.expectedMeetAt } : prev);
        }
      } catch {
        // 시간 연장 상태 조회 실패는 채팅 사용을 막지 않습니다.
      }
    };

    fetchExtension();
    const intervalId = window.setInterval(fetchExtension, 3000);
    return () => window.clearInterval(intervalId);
  }, [matchInfo]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleOpenMembers = async () => {
    if (!chatRoomId) return;

    setMembersOpen(true);
    setMembersLoading(true);
    try {
      const res = await getChatMembers(chatRoomId);
      setMembers(res.data.data);
    } catch (err: any) {
      setError(err.response?.data?.message || '참여자 목록을 불러오지 못했습니다.');
    } finally {
      setMembersLoading(false);
    }
  };

  const handleSend = (event: React.FormEvent) => {
    event.preventDefault();
    if (!isChatWritable || !message.trim() || !chatRoomId || !stompClient.current?.connected) return;

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
      const res = await acceptMeetExtension(matchInfo.matchId);
      const extendedMeetAt = res.data.data.extendedMeetAt;
      if (extendedMeetAt) {
        setExtendedMeetAt(matchInfo.matchId, extendedMeetAt);
        setMatchInfo((prev) => prev ? { ...prev, meetAt: extendedMeetAt } : prev);
      }
      setExtensionInfo((prev) => prev ? { ...prev, extensionStatus: 'ACCEPTED' } : prev);
    } catch (err: any) {
      alert(err.response?.data?.message || '시간 연장 수락에 실패했습니다.');
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
      alert(err.response?.data?.message || '시간 연장 거절에 실패했습니다.');
    } finally {
      setExtensionActionLoading(false);
    }
  };

  const loadMore = async () => {
    if (!hasNext || !cursor || !chatRoomId) return;

    try {
      const res = await getChatMessages(chatRoomId, cursor);
      setMessages((prev) => [...toChronologicalMessages(res.data.data.content), ...prev]);
      setCursor(res.data.data.nextCursor);
      setHasNext(res.data.data.hasNext);
    } catch (err) {
      console.error('Failed to load older messages', err);
    }
  };

  const canRequestExtension = !extensionInfo || extensionInfo.extensionStatus === 'NONE' || extensionInfo.extensionStatus === 'EXPIRED';
  const isApplicant = currentUserId !== null && matchInfo !== null && currentUserId === matchInfo.applicantId;

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="mb-4 animate-spin text-[#d84315]" size={40} />
        <p className="text-[#616161]">채팅방에 입장하는 중...</p>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100vh-12rem)] max-w-3xl flex-col">
      <div className="flex items-center justify-between rounded-t-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
        <div className="flex items-center gap-3">
          <Link to="/matches" className="text-[#616161] hover:text-[#d84315]"><ArrowLeft size={20} /></Link>
          <div>
            <h2 className="font-semibold text-[#212121]">{matchInfo ? `${matchInfo.placeName} 만남` : '채팅'}</h2>
            <p className="text-xs text-[#9e9e9e]">
              {matchInfo?.placeName} · {matchInfo?.meetAt ? new Date(matchInfo.meetAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2">
          {chatRoomId && (
            <button type="button" onClick={handleOpenMembers} className="flex items-center gap-2 rounded-xl border border-[#e0e0e0] bg-white px-3 py-2.5 text-sm font-semibold text-[#616161] shadow-sm transition-all hover:border-[#d84315] hover:text-[#d84315]" title="참여자 목록" aria-label="참여자 목록">
              <Users size={16} />
            </button>
          )}
          {!connected && !isReadOnlyChat && <span className="flex items-center gap-1 text-xs text-red-500"><AlertCircle size={12} /> 연결 끊김</span>}
          {matchInfo ? (
            <>
              <Link to={`/matches/${matchInfo.matchId}/place-verification`} className="flex items-center gap-2 rounded-xl bg-[#d84315] px-5 py-2.5 text-sm font-semibold text-white shadow-md transition-all hover:bg-[#bf360c]">
                <MapPin size={16} />장소 인증
              </Link>
              {isApplicant && (
                <button type="button" onClick={handleExtendMeetTime} disabled={extensionLoading || !canRequestExtension} className="flex items-center gap-2 rounded-xl border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] shadow-sm transition-all hover:bg-[#fff3e0] disabled:cursor-not-allowed disabled:opacity-50">
                  {extensionLoading ? <Loader2 size={16} className="animate-spin" /> : <Clock size={16} />}
                  시간 연장
                </button>
              )}
            </>
          ) : (
            <button type="button" disabled className="flex items-center gap-2 rounded-xl bg-[#e0e0e0] px-5 py-2.5 text-sm font-semibold text-white shadow-md"><MapPin size={16} />장소 인증</button>
          )}
        </div>
      </div>

      {error && <Banner tone="error">{error}</Banner>}
      {extensionInfo && extensionInfo.extensionStatus !== 'NONE' && matchInfo?.status === 'MATCHED' && (
        <ExtensionBanner
          extensionInfo={extensionInfo}
          loading={extensionActionLoading}
          onAccept={handleAcceptExtension}
          onReject={handleRejectExtension}
        />
      )}

      <div ref={scrollRef} className="flex-1 overflow-y-auto border-x border-[#e0e0e0] bg-white p-4">
        <div className="space-y-4">
          {hasNext && <button onClick={loadMore} className="w-full py-2 text-xs text-[#9e9e9e] hover:text-[#d84315]">이전 메시지 불러오기</button>}
          {messages.map((msg, index) => {
            const previous = messages[index - 1];
            const showDate = !previous || formatMessageDate(previous.createdAt) !== formatMessageDate(msg.createdAt);
            const isMine = msg.senderId === currentUserId;
            return (
              <div key={msg.messageId}>
                {showDate && <div className="my-5 text-center text-xs font-semibold text-[#9e9e9e]">{formatMessageDate(msg.createdAt)}</div>}
                <div className={`flex ${isMine ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[75%] ${isMine ? 'text-right' : 'text-left'}`}>
                    {!isMine && <p className="mb-1 text-xs font-semibold text-[#757575]">{msg.senderNickname}</p>}
                    <div className={`rounded-2xl px-4 py-3 text-sm leading-6 ${isMine ? 'bg-[#d84315] text-white' : 'bg-[#f5f5f5] text-[#212121]'}`}>{msg.content}</div>
                    <div className="mt-1 flex items-center justify-end gap-2 text-[11px]">
                      {isMine && <span className={msg.isRead ? 'text-[#bdbdbd]' : 'font-semibold text-[#d84315]'}>{msg.isRead ? '읽음' : '안읽음'}</span>}
                      <span className="text-[#9e9e9e]">{formatMessageTime(msg.createdAt)}</span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <form onSubmit={handleSend} className="flex items-center gap-3 rounded-b-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
        <input type="text" value={message} onChange={(event) => setMessage(event.target.value)} placeholder={isCancelledMatch || isReadOnlyChat || (matchInfo && matchInfo.status !== 'MATCHED') ? '조회만 가능한 채팅방입니다.' : connected ? '메시지를 입력하세요...' : '연결 중입니다...'} disabled={!isChatWritable} className="flex-1 rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315] disabled:bg-[#f5f5f5] disabled:text-[#9e9e9e]" />
        <button type="submit" disabled={!isChatWritable || !message.trim()} className="flex items-center gap-2 rounded-xl bg-[#d84315] px-6 py-3 font-semibold text-white shadow-md transition-all hover:bg-[#bf360c] hover:shadow-lg disabled:bg-[#e0e0e0]"><Send size={18} />전송</button>
      </form>

      {membersOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold text-[#d84315]">채팅방 참여자</p>
                <h3 className="text-lg font-bold text-[#212121]">현재 참여 중인 사용자</h3>
              </div>
              <button type="button" onClick={() => setMembersOpen(false)} className="rounded-full p-2 text-[#757575] hover:bg-[#f5f5f5] hover:text-[#212121]"><X size={18} /></button>
            </div>
            {membersLoading ? (
              <div className="py-8 text-center text-sm text-[#9e9e9e]"><Loader2 className="mx-auto mb-2 animate-spin text-[#d84315]" size={20} />참여자 목록을 불러오는 중...</div>
            ) : members.length > 0 ? (
              <div className="space-y-2">
                {members.map((member) => (
                  <div key={member.userId} className="flex items-center justify-between rounded-xl border border-[#eeeeee] px-4 py-3">
                    <span className="font-semibold text-[#212121]">{member.nickname || '탈퇴한 사용자'}</span>
                    <span className="text-xs text-[#9e9e9e]">참여 중</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-6 text-center text-sm text-[#9e9e9e]">표시할 참여자가 없습니다.</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ExtensionBanner({ extensionInfo, loading, onAccept, onReject }: { extensionInfo: MeetExtensionResponse; loading: boolean; onAccept: () => void; onReject: () => void }) {
  if (extensionInfo.extensionStatus === 'REQUESTED' && !extensionInfo.isMyRequest) {
    return (
      <div className="flex items-center justify-between gap-3 border-x border-b border-[#ff9800] bg-[#fff3e0] px-4 py-3">
        <div className="flex items-center gap-2 text-sm text-[#e65100]"><Clock size={16} /><span><strong>{extensionInfo.requesterNickname}</strong>님이 만남 시간 10분 연장을 요청했습니다.</span></div>
        <div className="flex gap-2">
          <button onClick={onAccept} disabled={loading} className="rounded-lg bg-[#4caf50] px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50">수락</button>
          <button onClick={onReject} disabled={loading} className="rounded-lg border border-[#ef5350] bg-white px-3 py-1.5 text-xs font-semibold text-[#ef5350] disabled:opacity-50">거절</button>
        </div>
      </div>
    );
  }

  if (extensionInfo.extensionStatus === 'REQUESTED' && extensionInfo.isMyRequest) return <Banner tone="info">연장 요청을 보냈습니다. 상대방의 응답을 기다리는 중...</Banner>;
  if (extensionInfo.extensionStatus === 'ACCEPTED') return <Banner tone="success"><Check size={16} />만남 시간이 10분 연장되었습니다.</Banner>;
  if (extensionInfo.extensionStatus === 'REJECTED' && extensionInfo.isMyRequest) return <Banner tone="error">상대방이 시간 연장 요청을 거절했습니다.</Banner>;
  if (extensionInfo.extensionStatus === 'EXPIRED' && extensionInfo.isMyRequest) return <Banner tone="muted">연장 요청이 만료되었습니다. 다시 요청할 수 있습니다.</Banner>;
  return null;
}

function Banner({ children, tone }: { children: React.ReactNode; tone: 'error' | 'info' | 'success' | 'muted' }) {
  const classes = {
    error: 'border-[#ef5350] bg-[#ffebee] text-[#c62828]',
    info: 'border-[#2196f3] bg-[#e3f2fd] text-[#1565c0]',
    success: 'border-[#4caf50] bg-[#e8f5e9] text-[#2e7d32]',
    muted: 'border-[#bdbdbd] bg-[#f5f5f5] text-[#757575]',
  }[tone];
  return <div className={`flex items-center gap-2 border-x border-b px-4 py-3 text-sm ${classes}`}>{children}</div>;
}

function formatMessageDate(value: string) {
  return new Date(value).toLocaleDateString('ko-KR', { year: 'numeric', month: 'numeric', day: 'numeric' });
}

function formatMessageTime(value: string) {
  return new Date(value).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: true });
}
