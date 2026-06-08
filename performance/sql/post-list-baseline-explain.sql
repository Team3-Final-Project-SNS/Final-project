-- 게시글 목록 조회 Baseline EXPLAIN
-- 인덱스 적용 전 실행 계획 캡처용입니다.
-- 실제 API 조건: GET /api/v1/posts?status=OPEN&page=0&size=20
-- 정렬 조건: author_deposit DESC

EXPLAIN
SELECT
    p.post_id,
    p.author_id,
    p.meet_at,
    p.place_name,
    p.place_lat,
    p.place_lng,
    p.content,
    p.author_deposit,
    p.status,
    p.max_applicants,
    p.current_applicants,
    p.created_at,
    p.updated_at,
    p.deleted_at
FROM posts p
WHERE p.deleted_at IS NULL
  AND p.status = 'OPEN'
  AND p.author_id IN (
      SELECT u.user_id
      FROM users u
      WHERE u.deleted_at IS NULL
        AND u.email LIKE 'perf-user-%@perf.local'
  )
ORDER BY p.author_deposit DESC
LIMIT 20 OFFSET 0;

-- Page 객체 생성을 위해 함께 실행되는 count 쿼리 기준 EXPLAIN입니다.
EXPLAIN
SELECT COUNT(p.post_id)
FROM posts p
WHERE p.deleted_at IS NULL
  AND p.status = 'OPEN'
  AND p.author_id IN (
      SELECT u.user_id
      FROM users u
      WHERE u.deleted_at IS NULL
        AND u.email LIKE 'perf-user-%@perf.local'
  );
