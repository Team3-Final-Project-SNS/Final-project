package com.example.team3final.domain.review.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetWrittenReviewsResponseDto;
import com.example.team3final.domain.review.service.ReviewService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Review", description = "후기 API - 후기 작성, 내가 작성한 후기 목록 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    // 공통 에러 응답 예시 상수

    // 401: 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 404: 매칭 없음
    private static final String EXAMPLE_404_MATCH = """
            {
              "success": false,
              "code": "MATCH_001",
              "message": "존재하지 않는 매칭입니다.",
              "data": null
            }
            """;

    /**
     * 후기 작성 - 매칭 완료 후 상대방 후기를 작성합니다.
     * 후기는 수정/삭제하지 않는 정책이므로, 같은 매칭에서 같은 작성자는 1회만 작성할 수 있습니다.
     */
    @Operation(
            summary = "후기 작성",
            description = """
                    매칭 완료 후 상대방(등록자)에 대한 후기를 작성합니다.
                    
                    **작성 가능 조건:**
                    - 매칭 상태가 COMPLETED여야 합니다.
                    - 만남 완료 시점으로부터 **7일 이내**에만 작성 가능합니다.
                    - **신청자만** 후기를 작성할 수 있습니다. (등록자는 작성 불가)
                    - 동일 매칭에서 1회만 작성 가능합니다. (수정/삭제 불가)
                    
                    **태그 정책:**
                    - `좋았어요` 태그와 `아쉬웠어요` 태그는 **동시에 선택 불가**합니다.
                    - 둘 중 하나는 반드시 선택해야 합니다.
                    - 아쉬웠어요 태그 중 `다시 만나고 싶지 않아요` 선택 시 → 양방향 블라인드 처리됩니다.
                    
                    **후기 작성 보상:** +50P 지급 (REVIEW_REWARD)
                    
                    **매너온도 반영:** 후기 태그 점수 기반으로 등록자의 매너온도가 갱신됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "후기 작성 성공 - +50P 보상 지급 + 등록자 매너온도 갱신"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "등록자는 후기 작성 불가 (REVIEW_001) / 매칭 당사자가 아님 (MATCH_NOT_PARTICIPANT)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "REVIEW_001",
                                      "message": "등록자는 후기를 작성할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 후기를 작성한 매칭 (REVIEW_003)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "REVIEW_003",
                                      "message": "이미 후기를 작성한 매칭입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "COMPLETED 상태 아님(REVIEW_002) / 작성 기간 만료 7일 초과(REVIEW_004) / 태그 규칙 위반(REVIEW_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "REVIEW_004",
                                      "message": "후기 작성 가능 기간이 만료되었습니다. (만남 완료 후 7일 이내)",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
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
     * 내가 작성한 후기 목록 조회
     * 사용자는 받은 후기 목록을 볼 수 없고, 본인이 작성한 후기만 확인할 수 있습니다.
     */
    @Operation(
            summary = "내가 작성한 후기 목록 조회",
            description = """
                    로그인한 사용자가 직접 작성한 후기 목록을 조회합니다.
                    - 본인이 **작성한** 후기만 조회됩니다. (받은 후기는 조회 불가)
                    - 최신순으로 정렬됩니다.
                    - 탈퇴한 상대방 정보는 `알 수 없음`으로 표시됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            )
    })
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
