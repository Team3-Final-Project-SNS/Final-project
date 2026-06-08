 -- 게시글 목록 조회 인덱스 적용
-- Baseline 병목:
-- - posts 테이블 type = ALL
-- - key = null
-- - Extra = Using where; Using filesort
--
-- 목표:
-- - status/deleted_at 조건으로 먼저 필터링
-- - author_deposit DESC 정렬 비용 감소
-- - author_id 조건 필터링 보조

CREATE INDEX idx_posts_status_deleted_deposit_author
    ON posts (status, deleted_at, author_deposit DESC, author_id);

-- 게시글 목록 조회 쿼리 인덱스 적용 후 EXPLAIN
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

-- Page 객체 생성을 위해 함께 실행되는 count 쿼리 기준 EXPLAIN
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

-- 인덱스를 되돌려야 할 때 사용합니다.
-- DROP INDEX idx_posts_status_deleted_deposit_author ON posts;
