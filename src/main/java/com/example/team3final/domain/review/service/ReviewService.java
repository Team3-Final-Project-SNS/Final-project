package com.example.team3final.domain.review.service;

import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetWrittenReviewsResponseDto;
import org.springframework.data.domain.Pageable;


/**
 * 후기 도메인의 핵심 비즈니스 로직을 정의하는 서비스입니다.
 *
 * 후기 작성, 받은 후기 조회, 태그 기반 점수 계산,
 * 매너 온도 재계산 흐름을 처리합니다.
 */
public interface ReviewService {


    /**
     * 매칭 완료 후 상대방에게 후기를 작성합니다.
     *
     * @param matchId 후기 작성 대상 매칭 ID
     * @param writerId 후기 작성자 ID
     * @param request 선택한 좋아요/아쉬워요 태그 요청
     * @return 생성된 후기 정보
     */
    CreateReviewResponseDto createReview(Long matchId, Long writerId, CreateReviewRequestDto request);


    /**
     * 로그인 사용자가 직접 작성한 후기 목록을 조회합니다.
     *
     * 사용자는 받은 후기 목록을 볼 수 없고, 본인이 작성한 후기만 확인할 수 있습니다.
     *
     * @param currentUserId 현재 로그인한 사용자 ID
     * @param pageable 페이지 요청 정보
     * @return 내가 작성한 후기 목록과 페이지 정보
     */
    GetWrittenReviewsResponseDto getWrittenReviews(Long currentUserId, Pageable pageable);
}
