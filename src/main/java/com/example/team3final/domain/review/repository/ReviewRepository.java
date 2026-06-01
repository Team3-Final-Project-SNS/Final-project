package com.example.team3final.domain.review.repository;

import com.example.team3final.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


/**
 * 후기 본문 엔티티를 조회/저장하는 Repository입니다.
 *
 * Review에는 matchId와 writerId만 저장하고 targetId는 저장하지 않습니다.
 * 받은 사람은 매칭의 등록자/신청자 관계를 기준으로 계산합니다.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 같은 매칭에서 같은 사용자가 이미 후기를 작성했는지 확인합니다.
     *
     * 후기 수정/삭제가 없고 1매칭당 1회만 작성 가능하므로,
     * 후기 생성 전에 중복 작성 방지용으로 사용합니다.
     */
    boolean existsByMatchIdAndWriterId(Long matchId, Long writerId);

    /**
     * 특정 사용자가 받은 후기 목록을 조회합니다.
     *
     * Review에 targetId를 저장하지 않기 때문에,
     * Match와 Post를 조인해서 작성자의 반대편 사용자가 targetUserId인 후기를 찾습니다.
     *
     * 조건:
     * - targetUserId가 게시글 작성자이면, 후기 작성자는 매칭 신청자여야 합니다.
     * - targetUserId가 매칭 신청자이면, 후기 작성자는 게시글 작성자여야 합니다.
     *
     * 매너 온도 재계산처럼 전체 받은 후기가 필요한 경우 사용합니다.
     */
    @Query("""
            SELECT r
            FROM Review r
            JOIN Match m ON r.matchId = m.id
            JOIN Post p ON m.postId = p.id
            WHERE (p.authorId = :targetUserId AND m.applicantId = r.writerId)
               OR (m.applicantId = :targetUserId AND p.authorId = r.writerId)
            """)
    List<Review> findReceivedReviews(@Param("targetUserId") Long targetUserId);



    /**
     * 특정 사용자가 받은 후기 목록을 페이징 조회합니다.
     *
     * 프로필 화면에서 받은 후기 목록을 페이지 단위로 보여줄 때 사용합니다.
     * 조회 조건은 전체 조회용 findReceivedReviews와 동일합니다.
     */
    @Query("""
            SELECT r
            FROM Review r
            JOIN Match m ON r.matchId = m.id
            JOIN Post p ON m.postId = p.id
            WHERE (p.authorId = :targetUserId AND m.applicantId = r.writerId)
               OR (m.applicantId = :targetUserId AND p.authorId = r.writerId)
            """)
    Page<Review> findReceivedReviews(@Param("targetUserId") Long targetUserId, Pageable pageable);
}
