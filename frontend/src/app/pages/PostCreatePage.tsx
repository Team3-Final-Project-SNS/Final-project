import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { createPost, getPost, updatePost } from '../../api/postApi';
import axiosInstance from '../../api/axiosInstance';
import { AlertCircle, ChevronDown, Loader2, MapPin, Search, X } from 'lucide-react';

// 카카오 장소 검색 결과 타입
interface KakaoPlace {
    place_name: string;   // 장소명
    y: string;            // 위도 (문자열로 옴 → Number() 변환 필요)
    x: string;            // 경도 (문자열로 옴 → Number() 변환 필요)
    address_name: string; // 도로명 주소
}

function toDateInputValue(value: Date) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function toTimeInputValue(value: Date) {
    const hours = String(value.getHours()).padStart(2, '0');
    const minutes = String(value.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
}

function toTenMinuteTimeInputValue(value: Date) {
    const rounded = new Date(value);
    const minutes = rounded.getMinutes();
    const nextTenMinute = Math.round(minutes / 10) * 10;

    if (nextTenMinute === 60) {
        rounded.setHours(rounded.getHours() + 1);
        rounded.setMinutes(0, 0, 0);
    } else {
        rounded.setMinutes(nextTenMinute, 0, 0);
    }

    return toTimeInputValue(rounded);
}

const hourOptions = Array.from({ length: 24 }, (_, index) => String(index).padStart(2, '0'));
const minuteOptions = ['00', '10', '20', '30', '40', '50'];
const MIN_DEPOSIT = 200;
const DEPOSIT_STEP = 100;

export default function PostCreatePage() {
    const navigate = useNavigate();
    const { id } = useParams();
    const isEditMode = Boolean(id);

    // ── 기존 상태 ────────────────────────────────────────
    const [placeName, setPlaceName] = useState('');
    const [placeLat, setPlaceLat] = useState<number | null>(null);
    const [placeLng, setPlaceLng] = useState<number | null>(null);
    const [date, setDate] = useState(() => {
        // UTC 시간에 9시간(540분)을 더해서 한국 시간으로 변환
        const kstOffset = 9 * 60 * 60 * 1000; // 9시간을 밀리초로 변환
        const kstDate = new Date(Date.now() + kstOffset);
        // toISOString()은 항상 UTC 기준이지만, 위에서 +9시간 했으므로 결과는 KST 날짜
        return kstDate.toISOString().split('T')[0]; // "2026-06-01"
    });
    const [time, setTime] = useState('12:30');
    const [content, setContent] = useState('');
    const [points, setPoints] = useState(1000);
    const [userPoints, setUserPoints] = useState(0);
    const [loading, setLoading] = useState(false);
    const [initialLoading, setInitialLoading] = useState(isEditMode);
    const [error, setError] = useState('');

    // ── 최대 참여 인원 (신규) ─────────────────────────────
    // 백엔드 CreatePostRequestDto.maxApplicants 필드 (최소 2 ~ 최대 10)
    const [maxApplicants, setMaxApplicants] = useState(2);

    // ── 장소 검색 관련 상태 ───────────────────────────────
    const [searchKeyword, setSearchKeyword] = useState('');
    const [searchResults, setSearchResults] = useState<KakaoPlace[]>([]);
    const [showResults, setShowResults] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);
    const [openTimeDropdown, setOpenTimeDropdown] = useState<'hour' | 'minute' | null>(null);

    // ── 유저 포인트 조회 ──────────────────────────────────
    useEffect(() => {
        const fetchUser = async () => {
            try {
                const res = await axiosInstance.get('/api/v1/users/me');
                setUserPoints(res.data.data.point);
            } catch (e) {
                console.error('Failed to fetch user info');
            }
        };
        fetchUser();
    }, []);

    useEffect(() => {
        if (!id) {
            return;
        }

        const fetchPost = async () => {
            setInitialLoading(true);
            setError('');
            try {
                const res = await getPost(Number(id));
                const post = res.data.data;
                const meetAt = new Date(post.meetAt);

                setPlaceName(post.placeName);
                setPlaceLat(post.placeLat);
                setPlaceLng(post.placeLng);
                setSearchKeyword(post.placeName);
                setDate(toDateInputValue(meetAt));
                setTime(toTenMinuteTimeInputValue(meetAt));
                setContent(post.content || '');
                setPoints(post.authorDeposit);
                setMaxApplicants(post.maxApplicants);
            } catch (err: any) {
                setError(err.response?.data?.message || '게시글 정보를 불러오지 못했습니다.');
            } finally {
                setInitialLoading(false);
            }
        };

        fetchPost();
    }, [id]);

    const afterPoints = userPoints - points;

    // ── 장소 검색 핸들러 ─────────────────────────────────
    const handleSearchPlace = () => {
        if (!searchKeyword.trim()) return;

        // kakao 객체 자체만 체크 (autoload=true라서 바로 사용 가능)
        if (!window.kakao) {
            setError('카카오맵 SDK가 로드되지 않았습니다. 새로고침 해주세요.');
            return;
        }

        setSearchLoading(true);
        setSearchResults([]);

        // autoload=true라서 load() 없이 바로 services 사용 가능
        const places = new window.kakao.maps.services.Places();
        places.keywordSearch(searchKeyword, (result: KakaoPlace[], status: string) => {
            setSearchLoading(false);
            if (status === window.kakao.maps.services.Status.OK) {
                setSearchResults(result.slice(0, 5));
                setShowResults(true);
            } else {
                setSearchResults([]);
                setShowResults(true);
            }
        });
    };

    // ── 검색 결과에서 장소 선택 ──────────────────────────
    const handleSelectPlace = (place: KakaoPlace) => {
        setPlaceName(place.place_name);
        setPlaceLat(Number(place.y)); // y = 위도
        setPlaceLng(Number(place.x)); // x = 경도
        setSearchKeyword(place.place_name);
        setShowResults(false);
        setError('');
    };

    // ── 장소 선택 초기화 ─────────────────────────────────
    const handleClearPlace = () => {
        setPlaceName('');
        setPlaceLat(null);
        setPlaceLng(null);
        setSearchKeyword('');
        setSearchResults([]);
        setShowResults(false);
    };

    // ── 엔터키로 검색 ────────────────────────────────────
    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleSearchPlace();
        }
    };

    // ── 게시글 등록/수정 ───────────────────────────────────────
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!placeName || !date || !time) {
            setError('필수 정보를 입력해주세요.');
            return;
        }
        if (placeLat === null || placeLng === null) {
            setError('만남 장소를 검색해서 선택해주세요.');
            return;
        }
        if (points < MIN_DEPOSIT) {
            setError('최소 책임비는 200포인트 입니다.');
            return;
        }

        setLoading(true);
        setError('');
        try {
            const meetAt = `${date}T${time}:00`;
            if (isEditMode && id) {
                await updatePost(Number(id), {
                    meetAt,
                    placeName,
                    placeLat,
                    placeLng,
                    content,
                    authorDeposit: points,
                });
                navigate(`/posts/${id}`);
            } else {
                await createPost({
                    meetAt,
                    placeName,
                    placeLat,
                    placeLng,
                    content,
                    authorDeposit: points,
                    maxApplicants
                });
                navigate('/posts');
            }
        } catch (err: any) {
            setError(err.response?.data?.message || (isEditMode ? '게시글 수정에 실패했습니다.' : '게시글 작성에 실패했습니다.'));
        } finally {
            setLoading(false);
        }
    };

    const pointOptions = [1000, 2000, 3000, 5000];
    const isPlaceSelected = placeLat !== null && placeLng !== null;
    const [selectedHour, selectedMinute] = time.split(':');
    const normalizedMinute = minuteOptions.includes(selectedMinute) ? selectedMinute : '00';

    const handleTimeChange = (nextHour: string, nextMinute: string) => {
        setTime(`${nextHour}:${nextMinute}`);
        setOpenTimeDropdown(null);
    };

    const selectDateOffset = (offsetDays: number) => {
        const nextDate = new Date();
        nextDate.setDate(nextDate.getDate() + offsetDays);
        setDate(toDateInputValue(nextDate));
    };

    const handlePointInputChange = (value: string) => {
        const nextPoint = Number(value);
        if (Number.isNaN(nextPoint)) return;

        setPoints(nextPoint);
        if (nextPoint < MIN_DEPOSIT) {
            setError('최소 책임비는 200포인트 입니다.');
        } else if (error === '최소 책임비는 200포인트 입니다.') {
            setError('');
        }
    };

    const adjustPoints = (delta: number) => {
        setPoints((current) => Math.max(MIN_DEPOSIT, current + delta));
        if (error === '최소 책임비는 200포인트 입니다.') {
            setError('');
        }
    };

    if (initialLoading) {
        return (
            <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="mb-4 animate-spin text-[#d84315]" size={40} />
                <p className="text-[#616161]">게시글 정보를 불러오는 중...</p>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-3xl font-bold text-[#212121] mb-2 text-center">
                {isEditMode ? '게시글 수정하기' : '밥 같이 먹을 사람 구하기 🍚'}
            </h1>
            <p className="text-center text-[#616161] mb-8">
                {isEditMode ? '변경할 내용을 수정한 뒤 저장해주세요' : '간단한 정보만 입력하면 매칭이 시작됩니다'}
            </p>

            <form onSubmit={handleSubmit} className="bg-white border border-[#e0e0e0] rounded-2xl p-8 shadow-sm">
                {error && (
                    <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
                        <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
                        <span className="text-[#c62828] text-sm">{error}</span>
                    </div>
                )}

                <div className="mb-6">
                    <h2 className="text-lg font-semibold text-[#212121] mb-4">모임 정보</h2>

                    <div className="space-y-4">
                        {/* 만남 장소 검색 */}
                        <div>
                            <label className="block text-sm font-medium text-[#424242] mb-2">
                                만남 장소
                            </label>
                            <div className="flex gap-2">
                                <div className="relative flex-1">
                                    <input
                                        type="text"
                                        value={searchKeyword}
                                        onChange={(e) => {
                                            setSearchKeyword(e.target.value);
                                            if (isPlaceSelected) {
                                                setPlaceLat(null);
                                                setPlaceLng(null);
                                                setPlaceName('');
                                            }
                                        }}
                                        onKeyDown={handleKeyDown}
                                        placeholder="장소명 검색 (예: 고려대학교 학생식당)"
                                        className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                                    />
                                    {searchKeyword && (
                                        <button
                                            type="button"
                                            onClick={handleClearPlace}
                                            className="absolute right-3 top-1/2 -translate-y-1/2 text-[#9e9e9e] hover:text-[#616161]"
                                        >
                                            <X size={16} />
                                        </button>
                                    )}
                                </div>
                                <button
                                    type="button"
                                    onClick={handleSearchPlace}
                                    disabled={searchLoading || !searchKeyword.trim()}
                                    className="flex shrink-0 items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-[#bf360c] disabled:bg-[#e0e0e0] disabled:text-[#9e9e9e]"
                                >
                                    {searchLoading
                                        ? <Loader2 size={16} className="animate-spin" />
                                        : <Search size={16} />
                                    }
                                    검색
                                </button>
                            </div>

                            {/* 검색 결과 드롭다운 */}
                            {showResults && (
                                <div className="mt-2 border border-[#e0e0e0] rounded-lg overflow-hidden shadow-md">
                                    {searchResults.length === 0 ? (
                                        <div className="px-4 py-3 text-sm text-[#9e9e9e] text-center">
                                            검색 결과가 없습니다.
                                        </div>
                                    ) : (
                                        searchResults.map((place, index) => (
                                            <button
                                                key={index}
                                                type="button"
                                                onClick={() => handleSelectPlace(place)}
                                                className="w-full flex items-start gap-3 px-4 py-3 hover:bg-[#fff3e0] transition-colors border-b border-[#f5f5f5] last:border-0 text-left"
                                            >
                                                <MapPin size={16} className="text-[#d84315] mt-0.5 shrink-0" />
                                                <div>
                                                    <p className="text-sm font-semibold text-[#212121]">
                                                        {place.place_name}
                                                    </p>
                                                    <p className="text-xs text-[#9e9e9e] mt-0.5">
                                                        {place.address_name}
                                                    </p>
                                                </div>
                                            </button>
                                        ))
                                    )}
                                </div>
                            )}

                            {/* 장소 선택 완료 표시 */}
                            {isPlaceSelected && (
                                <p className="mt-2 text-xs font-semibold text-[#4caf50] flex items-center gap-1">
                                    ✅ {placeName} 이(가) 만남 장소로 설정되었습니다.
                                </p>
                            )}
                        </div>

                        {/* 날짜/시간 */}
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-[#424242] mb-2">날짜</label>
                                <input
                                    type="date"
                                    value={date}
                                    onChange={(e) => setDate(e.target.value)}
                                    min={toDateInputValue(new Date())}
                                    className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                                    required
                                />
                                <div className="mt-2 flex gap-2">
                                    {[
                                        { label: '오늘', offset: 0 },
                                        { label: '내일', offset: 1 },
                                        { label: '모레', offset: 2 },
                                    ].map((item) => (
                                        <button
                                            key={item.label}
                                            type="button"
                                            onClick={() => selectDateOffset(item.offset)}
                                            className="rounded-full border border-[#e0e0e0] bg-white px-3 py-1.5 text-xs font-semibold text-[#616161] hover:border-[#d84315] hover:text-[#d84315]"
                                        >
                                            {item.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-[#424242] mb-2">시간</label>
                                <div className="grid grid-cols-2 gap-2">
                                    <div className="relative">
                                        <button
                                            type="button"
                                            onClick={() => setOpenTimeDropdown((prev) => (prev === 'hour' ? null : 'hour'))}
                                            className="flex w-full items-center justify-between rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-left focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                                        >
                                            <span>{selectedHour}시</span>
                                            <ChevronDown size={18} className="text-[#616161]" />
                                        </button>
                                        {openTimeDropdown === 'hour' && (
                                            <div className="absolute left-0 right-0 top-[calc(100%+6px)] z-30 max-h-56 overflow-y-auto rounded-lg border border-[#e0e0e0] bg-white shadow-xl">
                                                {hourOptions.map((hour) => (
                                                    <button
                                                        key={hour}
                                                        type="button"
                                                        onClick={() => handleTimeChange(hour, normalizedMinute)}
                                                        className={`w-full px-4 py-3 text-left text-sm transition-colors hover:bg-[#fff3e0] ${
                                                            selectedHour === hour ? 'bg-[#fff3e0] font-bold text-[#d84315]' : 'bg-white text-[#212121]'
                                                        }`}
                                                    >
                                                        {hour}시
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                    <div className="relative">
                                        <button
                                            type="button"
                                            onClick={() => setOpenTimeDropdown((prev) => (prev === 'minute' ? null : 'minute'))}
                                            className="flex w-full items-center justify-between rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-left focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                                        >
                                            <span>{normalizedMinute}분</span>
                                            <ChevronDown size={18} className="text-[#616161]" />
                                        </button>
                                        {openTimeDropdown === 'minute' && (
                                            <div className="absolute left-0 right-0 top-[calc(100%+6px)] z-30 overflow-hidden rounded-lg border border-[#e0e0e0] bg-white shadow-xl">
                                                {minuteOptions.map((minute) => (
                                                    <button
                                                        key={minute}
                                                        type="button"
                                                        onClick={() => handleTimeChange(selectedHour, minute)}
                                                        className={`w-full px-4 py-3 text-left text-sm transition-colors hover:bg-[#fff3e0] ${
                                                            normalizedMinute === minute ? 'bg-[#fff3e0] font-bold text-[#d84315]' : 'bg-white text-[#212121]'
                                                        }`}
                                                    >
                                                        {minute}분
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>

                        {!isEditMode && (
                            <div>
                                <label className="block text-sm font-medium text-[#424242] mb-2">
                                    최대 참여 인원
                                </label>
                                <div className="flex gap-2 flex-wrap">
                                    {[2, 3, 4, 5, 6, 7, 8, 9, 10].map((num) => (
                                        <button
                                            key={num}
                                            type="button"
                                            onClick={() => setMaxApplicants(num)}
                                            className={`px-4 py-3 rounded-lg font-semibold transition-colors ${
                                                maxApplicants === num
                                                    ? 'bg-[#d84315] text-white'
                                                    : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                                            }`}
                                        >
                                            {num}명
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* 한마디 */}
                        <div>
                            <label className="block text-sm font-medium text-[#424242] mb-2">한마디 (선택)</label>
                            <textarea
                                value={content}
                                onChange={(e) => setContent(e.target.value)}
                                placeholder="한마디를 남기면 더 잘 맞는 사람과 매칭될 수 있어요."
                                rows={4}
                                className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent resize-none"
                            />
                        </div>
                    </div>
                </div>

                {/* 책임비 설정 */}
                <div className="mb-6">
                    <h2 className="text-lg font-semibold text-[#212121] mb-2">책임비 설정</h2>
                    <p className="text-sm text-[#616161] mb-4">약속 이행의 증거</p>
                    <div className="flex gap-2 flex-wrap mb-4">
                        {pointOptions.map((option) => (
                            <button
                                key={option}
                                type="button"
                                onClick={() => setPoints(option)}
                                className={`px-6 py-3 rounded-lg font-semibold transition-colors ${
                                    points === option
                                        ? 'bg-[#d84315] text-white'
                                        : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                                }`}
                            >
                                {option.toLocaleString()}P
                            </button>
                        ))}
                        <input
                            type="number"
                            step={DEPOSIT_STEP}
                            placeholder="직접입력"
                            value={points}
                            onChange={(e) => handlePointInputChange(e.target.value)}
                            className="px-4 py-3 border border-[#e0e0e0] rounded-lg w-32 focus:outline-none focus:ring-2 focus:ring-[#d84315]"
                        />
                        <div className="flex overflow-hidden rounded-lg border border-[#e0e0e0] bg-white">
                            <button
                                type="button"
                                onClick={() => adjustPoints(-DEPOSIT_STEP)}
                                className="px-4 py-3 font-bold text-[#616161] hover:bg-[#fff3e0] disabled:text-[#bdbdbd]"
                                disabled={points <= MIN_DEPOSIT}
                            >
                                -
                            </button>
                            <button
                                type="button"
                                onClick={() => adjustPoints(DEPOSIT_STEP)}
                                className="border-l border-[#e0e0e0] px-4 py-3 font-bold text-[#616161] hover:bg-[#fff3e0]"
                            >
                                +
                            </button>
                        </div>
                    </div>
                    <div className="bg-[#fff3e0] rounded-lg p-4">
                        <p className="text-sm text-[#616161]">
                            현재 잔액 <strong className="text-[#d84315]">{userPoints.toLocaleString()}P</strong> → 신청 후 잔액:{' '}
                            <strong className="text-[#4caf50]">{afterPoints.toLocaleString()}P</strong>
                        </p>
                    </div>
                </div>

                {/* 제출 버튼 */}
                <button
                    type="submit"
                    disabled={loading}
                    className="w-full bg-[#d84315] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg disabled:bg-[#e0e0e0] flex items-center justify-center"
                >
                    {loading ? <Loader2 className="animate-spin" /> : isEditMode ? '수정 완료' : '게시글 올리기'}
                </button>
            </form>
        </div>
    );
}
