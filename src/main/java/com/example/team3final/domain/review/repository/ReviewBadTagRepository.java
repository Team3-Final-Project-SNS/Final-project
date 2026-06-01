package com.example.team3final.domain.review.repository;

import com.example.team3final.domain.review.entity.ReviewBadTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


/**
 * 후기에서 선택한 아쉬워요 태그를 조회/저장하는 Repository입니다.
 *
 * Review 엔티티와 태그를 분리해 저장하므로,
 * 하나의 후기에 여러 개의 아쉬워요 태그를 연결할 수 있습니다.
 */
public interface ReviewBadTagRepository extends JpaRepository<ReviewBadTagEntity, Long> {


    /**
     * 여러 후기 ID에 연결된 아쉬워요 태그 목록을 한 번에 조회합니다.
     *
     * 받은 후기 목록 조회 시 N+1 조회를 피하기 위해 사용합니다.
     */
    List<ReviewBadTagEntity> findByReviewIdIn(List<Long> reviewIds);
}
