import { useState } from 'react';
import { useParams, Link } from 'react-router';
import { ArrowLeft, Send, MapPin } from 'lucide-react';

interface Message {
  id: number;
  sender: 'me' | 'other';
  content: string;
  timestamp: string;
}

export default function ChatPage() {
  const { roomId } = useParams();
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    { id: 1, sender: 'other', content: '밥먹자', timestamp: '2026년 5월 14일' },
    { id: 2, sender: 'me', content: '오 저도 1층 좋아요!', timestamp: '' },
    { id: 3, sender: 'other', content: '안녕하세요! 저도 학생식당 1층 가려고 했어요 😊', timestamp: '' },
    { id: 4, sender: 'me', content: '오 저도 1층 좋아요! 12시 30분에 입구 앞에서 만날까요?', timestamp: '' },
    { id: 5, sender: 'other', content: '오 좋아요! 12시 30분에 입구 앞에서 만나요. 근데 혹시 어디 잘 아시나요?', timestamp: '' },
    { id: 6, sender: 'me', content: '감자하고 반갑습니다 ㅎㅎ', timestamp: '' },
  ]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) return;

    setMessages([
      ...messages,
      {
        id: messages.length + 1,
        sender: 'me',
        content: message,
        timestamp: '',
      },
    ]);
    setMessage('');
  };

  return (
      <div className="max-w-3xl mx-auto h-[calc(100vh-12rem)] flex flex-col">
        <div className="bg-white border border-[#e0e0e0] rounded-t-2xl p-5 flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-3">
            <Link to="/matches" className="text-[#616161] hover:text-[#d84315]">
              <ArrowLeft size={20} />
            </Link>
            <div>
              <h2 className="font-semibold text-[#212121]">밥먹자</h2>
              <p className="text-xs text-[#9e9e9e]">학생식당 1층 · 오후 12:30</p>
            </div>
          </div>

          <Link
              to={`/matches/${roomId}/place-verification`}
              className="px-5 py-2.5 bg-[#d84315] text-white rounded-xl text-sm font-semibold hover:bg-[#bf360c] transition-all shadow-md flex items-center gap-2"
          >
            <MapPin size={16} />
            장소 인증
          </Link>
        </div>

        <div className="flex-1 bg-white border-x border-[#e0e0e0] p-4 overflow-y-auto">
          <div className="space-y-4">
            {messages.map((msg, idx) => {
              const showDate = idx === 0 || msg.timestamp !== messages[idx - 1].timestamp;

              return (
                  <div key={msg.id}>
                    {showDate && msg.timestamp && (
                        <div className="text-center text-xs text-[#9e9e9e] my-4">{msg.timestamp}</div>
                    )}

                    <div className={`flex ${msg.sender === 'me' ? 'justify-end' : 'justify-start'}`}>
                      <div
                          className={`max-w-[70%] px-4 py-2.5 rounded-lg ${
                              msg.sender === 'me'
                                  ? 'bg-[#d84315] text-white'
                                  : 'bg-[#f5f5f5] text-[#212121]'
                          }`}
                      >
                        <p className="text-sm">{msg.content}</p>
                      </div>
                    </div>
                  </div>
              );
            })}
          </div>
        </div>

        <form
            onSubmit={handleSend}
            className="bg-white border border-[#e0e0e0] rounded-b-2xl p-5 flex items-center gap-3 shadow-sm"
        >
          <input
              type="text"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="메시지를 입력하세요..."
              className="flex-1 px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
          />
          <button
              type="submit"
              className="px-6 py-3 bg-[#d84315] text-white rounded-xl font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg flex items-center gap-2"
          >
            <Send size={18} />
            전송
          </button>
        </form>
      </div>
  );
}
