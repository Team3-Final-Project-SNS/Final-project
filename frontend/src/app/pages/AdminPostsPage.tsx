import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowLeft, Eye, FileText, Loader2, Search, Trash2, X } from 'lucide-react';
import { deleteAdminPost, getAdminPost, getAdminPosts, AdminPostDetail, AdminPostItem } from '../../api/adminPostApi';
import { PostStatus } from '../../api/postApi';
import { getUniversities, UniversityResponse } from '../../api/univApi';
import AdminFloatingChatbot from '../components/AdminFloatingChatbot';

const statusLabels: Record<PostStatus, string> = {
  OPEN: '모집중',
  MATCHED: '매칭됨',
  COMPLETED: '완료',
  CANCELLED: '취소',
};

const statusClasses: Record<PostStatus, string> = {
  OPEN: 'bg-[#e8f5e9] text-[#2e7d32]',
  MATCHED: 'bg-[#fff3e0] text-[#ef6c00]',
  COMPLETED: 'bg-[#e3f2fd] text-[#1565c0]',
  CANCELLED: 'bg-[#ffebee] text-[#c62828]',
};

export default function AdminPostsPage() {
  const [posts, setPosts] = useState<AdminPostItem[]>([]);
  const [universities, setUniversities] = useState<UniversityResponse[]>([]);
  const [universityId, setUniversityId] = useState('ALL');
  const [status, setStatus] = useState<'ALL' | PostStatus>('ALL');
  const [searchType, setSearchType] = useState<'PLACE' | 'AUTHOR'>('PLACE');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);
  const [selectedPost, setSelectedPost] = useState<AdminPostDetail | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminPostItem | null>(null);
  const [deleteReason, setDeleteReason] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [message, setMessage] = useState('');

  const loadPosts = async (nextPage = page) => {
    setLoading(true);
    setMessage('');
    try {
      const response = await getAdminPosts(
        universityId === 'ALL' ? undefined : Number(universityId),
        searchType === 'AUTHOR' ? keyword.trim() || undefined : undefined,
        status === 'ALL' ? undefined : status,
        searchType === 'PLACE' ? keyword.trim() || undefined : undefined,
        nextPage,
        20,
      );
      setPosts(response.data.data.content);
      setTotalPages(response.data.data.totalPages || 1);
      setPage(response.data.data.page);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '게시글 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const loadUniversities = async () => {
      try {
        const response = await getUniversities();
        setUniversities(response.data.data);
      } catch (err) {
        console.error('학교 목록 조회 실패', err);
      }
    };

    loadUniversities();
  }, []);

  useEffect(() => {
    loadPosts(0);
  }, []);

  const handleSearch = () => {
    loadPosts(0);
  };

  const handleOpenDetail = async (postId: number) => {
    setDetailLoadingId(postId);
    setMessage('');

    try {
      const response = await getAdminPost(postId);
      setSelectedPost(response.data.data);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '게시글 상세 정보를 불러오지 못했습니다.');
    } finally {
      setDetailLoadingId(null);
    }
  };

  const openDeleteModal = (post: AdminPostItem) => {
    setDeleteTarget(post);
    setDeleteReason('');
    setDeleteError('');
  };

  const closeDeleteModal = () => {
    if (deletingId !== null) {
      return;
    }

    setDeleteTarget(null);
    setDeleteReason('');
    setDeleteError('');
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }

    const trimmedReason = deleteReason.trim();

    if (!trimmedReason) {
      setDeleteError('삭제 사유는 필수입니다.');
      return;
    }

    setDeletingId(deleteTarget.postId);
    setMessage('');
    setDeleteError('');
    try {
      await deleteAdminPost(deleteTarget.postId, null, trimmedReason);
      setMessage(`게시글 #${deleteTarget.postId}을 삭제했습니다.`);
      setDeleteTarget(null);
      setDeleteReason('');
      setDeleteError('');
      await loadPosts(page);
    } catch (err: any) {
      setDeleteError(err.response?.data?.message || '게시글 삭제에 실패했습니다.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
          <ArrowLeft size={16} />
          관리자 콘솔
        </Link>

        <div className="mb-6 flex items-center gap-3">
          <FileText className="text-[#d84315]" size={28} />
          <div>
            <h1 className="text-3xl font-bold text-[#212121]">게시글 보기</h1>
            <p className="mt-1 text-sm text-[#757575]">학교별 게시글을 조회하고 신고 근거로 강제 삭제합니다.</p>
          </div>
        </div>

        <div className="mb-5 grid gap-3 rounded-2xl border border-[#e0e0e0] bg-white p-4 md:grid-cols-[1fr_1fr_1fr_2fr_auto]">
          <select
            value={universityId}
            onChange={(event) => setUniversityId(event.target.value)}
            className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none focus:border-[#d84315]"
          >
            <option value="ALL">전체 학교</option>
            {universities.map((university) => (
              <option key={university.universityId} value={university.universityId}>
                {university.universityName}
              </option>
            ))}
          </select>

          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as 'ALL' | PostStatus)}
            className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none focus:border-[#d84315]"
          >
            <option value="ALL">전체 상태</option>
            <option value="OPEN">모집중</option>
            <option value="MATCHED">매칭됨</option>
            <option value="COMPLETED">완료</option>
            <option value="CANCELLED">취소</option>
          </select>

          <select
            value={searchType}
            onChange={(event) => setSearchType(event.target.value as 'PLACE' | 'AUTHOR')}
            className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none focus:border-[#d84315]"
          >
            <option value="PLACE">장소 검색</option>
            <option value="AUTHOR">작성자 검색</option>
          </select>

          <label className="flex h-11 items-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-3">
            <Search size={16} className="text-[#9e9e9e]" />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  handleSearch();
                }
              }}
              placeholder={searchType === 'AUTHOR' ? '작성자 닉네임 검색' : '장소명 검색'}
              className="w-full text-sm outline-none"
            />
          </label>

          <button
            type="button"
            onClick={handleSearch}
            className="h-11 rounded-lg bg-[#d84315] px-5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#bf360c]"
          >
            조회
          </button>
        </div>

        {message && (
          <div className="mb-5 rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-sm font-semibold text-[#616161]">
            {message}
          </div>
        )}

        {loading ? (
          <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center text-[#9e9e9e]">
            <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
            게시글 목록을 불러오는 중...
          </div>
        ) : (
          <div className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
            <div className="grid grid-cols-[0.5fr_0.8fr_1fr_2.5fr_1fr_0.7fr_0.7fr_1fr] gap-4 border-b border-[#eeeeee] bg-[#fafafa] px-5 py-3 text-xs font-bold text-[#757575]">
              <span>ID</span>
              <span>작성자</span>
              <span>장소</span>
              <span>한마디</span>
              <span>만남 시간</span>
              <span>책임비</span>
              <span>상태</span>
              <span>관리</span>
            </div>

            {posts.length > 0 ? (
              posts.map((post) => (
                <div
                  key={post.postId}
                  className="grid grid-cols-[0.5fr_0.8fr_1fr_2.5fr_1fr_0.7fr_0.7fr_1fr] items-start gap-4 border-b border-[#f5f5f5] px-5 py-5 text-sm last:border-b-0"
                >
                  <span className="font-bold text-[#757575]">#{post.postId}</span>
                  <span className="font-bold text-[#212121]">{post.authorNickname}</span>
                  <span className="text-[#616161]">{post.placeName}</span>
                  <span className={`whitespace-pre-wrap break-words leading-6 ${post.content ? 'text-[#424242]' : 'text-[#9e9e9e]'}`}>
                    {post.content || '한마디 없음'}
                  </span>
                  <span className="text-xs font-semibold text-[#616161]">{formatDateTime(post.meetAt)}</span>
                  <span className="font-bold text-[#d84315]">{post.authorDeposit.toLocaleString()}P</span>
                  <span>
                    <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[post.status]}`}>
                      {statusLabels[post.status]}
                    </span>
                  </span>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      disabled={detailLoadingId === post.postId}
                      onClick={() => handleOpenDetail(post.postId)}
                      className="inline-flex items-center justify-center gap-1 rounded-lg border border-[#e0e0e0] px-3 py-2 text-xs font-bold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315] disabled:opacity-60"
                    >
                      {detailLoadingId === post.postId ? <Loader2 className="animate-spin" size={14} /> : <Eye size={14} />}
                      상세
                    </button>
                    <button
                      type="button"
                      disabled={deletingId === post.postId}
                      onClick={() => openDeleteModal(post)}
                      className="inline-flex items-center justify-center gap-1 rounded-lg border border-red-200 px-3 py-2 text-xs font-bold text-red-500 transition-colors hover:bg-red-50 disabled:opacity-60"
                    >
                      {deletingId === post.postId ? <Loader2 className="animate-spin" size={14} /> : <Trash2 size={14} />}
                      삭제
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <div className="p-12 text-center text-sm font-semibold text-[#9e9e9e]">
                조건에 맞는 게시글이 없습니다.
              </div>
            )}
          </div>
        )}

        {totalPages > 1 && (
          <div className="mt-5 flex items-center justify-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => loadPosts(page - 1)}
              className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
            >
              &lt;
            </button>
            {[...Array(totalPages)].map((_, index) => (
              <button
                key={index}
                type="button"
                onClick={() => loadPosts(index)}
                className={`rounded px-3 py-1.5 text-sm ${
                  page === index ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] hover:bg-[#f5f5f5]'
                }`}
              >
                {index + 1}
              </button>
            ))}
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => loadPosts(page + 1)}
              className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
            >
              &gt;
            </button>
          </div>
        )}
      </main>
      {selectedPost && (
        <PostDetailModal post={selectedPost} onClose={() => setSelectedPost(null)} />
      )}
      {deleteTarget && (
        <DeletePostModal
          post={deleteTarget}
          reason={deleteReason}
          error={deleteError}
          isDeleting={deletingId === deleteTarget.postId}
          onReasonChange={setDeleteReason}
          onClose={closeDeleteModal}
          onDelete={handleDelete}
        />
      )}
      <AdminFloatingChatbot />
    </div>
  );
}

function DeletePostModal({
  post,
  reason,
  error,
  isDeleting,
  onReasonChange,
  onClose,
  onDelete,
}: {
  post: AdminPostItem;
  reason: string;
  error: string;
  isDeleting: boolean;
  onReasonChange: (value: string) => void;
  onClose: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
      <div className="w-full max-w-xl rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold text-red-500">게시글 #{post.postId} 강제 삭제</p>
            <h2 className="mt-1 text-2xl font-bold text-[#212121]">{post.placeName}</h2>
            <p className="mt-1 text-sm font-semibold text-[#757575]">관리자 판단으로 게시글을 강제 삭제합니다.</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isDeleting}
            className="rounded-full p-2 text-[#757575] hover:bg-[#f5f5f5] hover:text-[#212121] disabled:opacity-50"
          >
            <X size={20} />
          </button>
        </div>

        {error && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-600">
            <AlertCircle size={17} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="space-y-4">
          <label className="block">
            <span className="mb-2 block text-sm font-bold text-[#616161]">삭제 사유</span>
            <textarea
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
              disabled={isDeleting}
              rows={4}
              placeholder="예: 스팸, 운영 정책 위반, 관리자 판단 삭제"
              className="w-full resize-none rounded-lg border border-[#e0e0e0] px-4 py-3 text-sm font-semibold outline-none focus:border-[#d84315] disabled:bg-[#f5f5f5]"
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isDeleting}
            className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-5 text-sm font-bold text-[#616161] transition-colors hover:bg-[#f5f5f5] disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onDelete}
            disabled={isDeleting}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-red-500 px-5 text-sm font-bold text-white transition-colors hover:bg-red-600 disabled:opacity-60"
          >
            {isDeleting ? <Loader2 className="animate-spin" size={16} /> : <Trash2 size={16} />}
            강제 삭제
          </button>
        </div>
      </div>
    </div>
  );
}

function PostDetailModal({ post, onClose }: { post: AdminPostDetail; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold text-[#d84315]">게시글 #{post.postId}</p>
            <h2 className="mt-1 text-2xl font-bold text-[#212121]">{post.placeName}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-[#757575] hover:bg-[#f5f5f5] hover:text-[#212121]"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-3 rounded-xl bg-[#fafafa] p-4 text-sm">
          <InfoRow label="작성자" value={post.authorNickname} />
          <InfoRow label="상태" value={statusLabels[post.status]} />
          <InfoRow label="책임비" value={`${post.authorDeposit.toLocaleString()}P`} />
          <InfoRow label="만남 시간" value={formatDateTime(post.meetAt)} />
          <InfoRow label="작성일" value={formatDateTime(post.createdAt)} />
        </div>

        <div className="mt-4 rounded-xl border border-[#eeeeee] p-4">
          <p className="mb-2 text-sm font-bold text-[#616161]">한마디</p>
          <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">
            {post.content || '작성된 한마디가 없습니다.'}
          </p>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="font-bold text-[#757575]">{label}</span>
      <span className="text-right font-semibold text-[#212121]">{value}</span>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
