package com.example.team3final.domain.review.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetReceivedReviewsResponseDto;
import com.example.team3final.domain.review.service.ReviewService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * 특정 유저가 받은 후기 목록을 조회합니다.
     *
     * 본인이거나 같은 학교 유저인 경우에만 조회할 수 있습니다.
     */
    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<ApiResponseDto<GetReceivedReviewsResponseDto>> getReviews(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        // 음수 page 값이 들어와도 첫 페이지로 보정합니다.
        int safePage = Math.max(page, 0);

        // size는 최소 1개, 최대 10개까지만 허용합니다.
        // 0 이하가 들어오면 1로 보정하고, 10을 초과하면 10으로 제한합니다.
        int safeSize = Math.max(1, Math.min(size, 10));

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by("createdAt").descending()
        );

        GetReceivedReviewsResponseDto response = reviewService.getReceivedReviews(
                userId,
                userDetails.getUserId(),
                pageable
        );

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
