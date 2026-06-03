package com.example.team3final.domain.review.repository;

import com.example.team3final.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


/**
 * 후기 본문 엔티티를 조회/저장하는 Repository입니다.
 *
 * 사용자는 본인이 작성한 후기만 조회할 수 있습니다.
 * 받은 후기 목록은 사용자에게 공개하지 않고, 매너 온도만 공개 지표로 제공합니다.
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
     * 로그인 사용자가 직접 작성한 후기 목록을 조회합니다.
     *
     * 사용자에게는 받은 후기 목록을 공개하지 않고,
     * 본인이 작성한 후기만 확인할 수 있게 합니다.
     */
    Page<Review> findAllByWriterIdOrderByCreatedAtDesc(Long writerId, Pageable pageable);

    /**
     * 같은 게시글에 속한 여러 매칭의 리뷰를 한 번에 조회합니다.
     *
     * 단체 만남에서는 신청자 여러 명이 각각 다른 matchId로 리뷰를 작성하므로,
     * postId에 속한 matchId 목록을 받아 전체 리뷰 평균을 계산할 때 사용합니다.
     */
    List<Review> findAllByMatchIdIn(List<Long> matchIds);

}
