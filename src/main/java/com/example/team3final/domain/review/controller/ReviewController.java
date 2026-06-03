package com.example.team3final.domain.review.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetWrittenReviewsResponseDto;
import com.example.team3final.domain.review.service.ReviewService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 매칭 완료 후 상대방 후기를 작성합니다.
     *
     * 후기는 수정/삭제하지 않는 정책이므로, 같은 매칭에서 같은 작성자는 1회만 작성할 수 있습니다.
     */
    @PostMapping("/matches/{matchId}/reviews")
    public ResponseEntity<ApiResponseDto<CreateReviewResponseDto>> createReview(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody CreateReviewRequestDto request
    ) {
        // 요청에서 받은 값과 인증 유저 ID를 서비스에 넘기는 연결 코드
        CreateReviewResponseDto response = reviewService.createReview(
                matchId,
                userDetails.getUserId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    /**
     * 로그인 사용자가 직접 작성한 후기 목록을 조회합니다.
     *
     * 사용자는 받은 후기 목록을 볼 수 없고, 본인이 작성한 후기만 확인할 수 있습니다.
     */
    @GetMapping("/me/reviews")
    public ResponseEntity<ApiResponseDto<GetWrittenReviewsResponseDto>> getReviews(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        GetWrittenReviewsResponseDto response = reviewService.getWrittenReviews(
                userDetails.getUserId()
        );

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
