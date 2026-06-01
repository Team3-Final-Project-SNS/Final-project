package com.example.team3final.domain.review.repository;

import com.example.team3final.domain.review.entity.ReviewGoodTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewGoodTagRepository extends JpaRepository<ReviewGoodTagEntity, Long> {


    /**
     * 여러 후기 ID에 연결된 좋아요 태그 목록을 한 번에 조회합니다.
     *
     * 받은 후기 목록 조회 시 N+1 조회를 피하기 위해 사용합니다.
     */
    List<ReviewGoodTagEntity> findByReviewIdIn(List<Long> reviewIds);
}
