import { useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Loader2, MessageSquare } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { DeletedPostReasonResponse, getDeletedPostReason } from '../../api/postApi';

export default function DeletedPostReasonPage() {
  const { id } = useParams();
  const postId = Number(id);
  const [post, setPost] = useState<DeletedPostReasonResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadReason = async () => {
      if (!Number.isInteger(postId) || postId <= 0) {
        setError('유효하지 않은 게시글입니다.');
        setLoading(false);
        return;
      }

      try {
        const response = await getDeletedPostReason(postId);
        setPost(response.data.data);
      } catch (err: any) {
        setError(err.response?.data?.message || '게시글 삭제 사유를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    loadReason();
  }, [postId]);

  if (loading) {
    return (
      <div className="flex min-h-[45vh] items-center justify-center gap-2 text-sm text-[#757575]">
        <Loader2 className="animate-spin text-[#d84315]" size={20} />
        삭제 사유를 불러오는 중...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/posts" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
        <ArrowLeft size={16} />
        게시글 목록
      </Link>

      <section className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
        <div className="border-b border-[#eeeeee] px-6 py-6">
          <p className="text-xs font-bold text-[#d84315]">삭제된 게시글</p>
          <h1 className="mt-2 text-2xl font-bold text-[#212121]">
            {post?.placeName || '이 게시글은 삭제되었습니다.'}
          </h1>
          {post?.deletedAt && (
            <p className="mt-2 text-sm text-[#9e9e9e]">{formatDateTime(post.deletedAt)} 삭제</p>
          )}
        </div>

        <div className="p-6">
          {error ? (
            <div className="flex gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              <AlertCircle className="shrink-0" size={18} />
              {error}
            </div>
          ) : (
            <>
              <div className="rounded-xl border border-[#ffccbc] bg-[#fff8f5] p-5">
                <p className="mb-2 text-sm font-bold text-[#bf360c]">삭제 사유</p>
                <p className="whitespace-pre-wrap text-sm leading-6 text-[#5d4037]">
                  {post?.deleteReason
                    ? `해당 게시글은 ${post.deleteReason} 로 인해 삭제되었습니다.`
                    : '해당 게시글은 작성자에 의해 삭제되었습니다.'}
                </p>
              </div>

              <div className="mt-5 rounded-xl bg-[#fafafa] p-4 text-sm leading-6 text-[#616161]">
                삭제 처리에 관한 문의가 필요하다면 고객센터에 문의해 주세요.
              </div>

              <div className="mt-5 flex justify-end">
                <Link
                  to="/me/support/inquiries"
                  className="inline-flex items-center gap-2 rounded-lg bg-[#d84315] px-5 py-3 text-sm font-bold text-white hover:bg-[#bf360c]"
                >
                  <MessageSquare size={16} />
                  고객센터 문의
                </Link>
              </div>
            </>
          )}
        </div>
      </section>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
