package com.example.team3final.domain.post.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.*;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostCommandService;
import com.example.team3final.domain.post.service.PostQueryService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post", description = "게시글 API") // Swagger UI에서 컨트롤러를 그룹핑
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;

    // 공통 에러 응답 예시 상수
    // 여러 API에서 동일한 에러 응답이 반복되므로 상수로 분리하여 중복 제거

    // 401: 인증 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 403: 본인 게시글이 아님
    private static final String EXAMPLE_403_NOT_AUTHOR = """
            {
              "success": false,
              "code": "POST_005",
              "message": "본인 게시글만 수정/삭제할 수 있습니다.",
              "data": null
            }
            """;

    // 404: 게시글 없음
    private static final String EXAMPLE_404 = """
            {
              "success": false,
              "code": "POST_001",
              "message": "존재하지 않는 게시글입니다.",
              "data": null
            }
            """;

    /**
     * 게시글 작성
     * POST /api/v1/posts
     */
    @Operation(
            summary = "게시글 작성",
            description = """
                    새 밥약 게시글을 등록합니다.
                    - 작성 즉시 책임비 포인트가 예치됩니다. (잔액 차감)
                    - 책임비는 최소 200P 이상, 100P 단위로만 설정 가능합니다.
                    - 포인트 잔액이 부족하면 작성이 불가합니다.
                    - 만남 희망 시간은 현재 이후여야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "게시글 작성 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패 (만남시간 오류 POST_003 / 책임비 단위 오류 POST_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POST_004",
                                      "message": "책임비 포인트는 최소 200P 이상, 100P 단위여야 합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "포인트 잔액 부족 (POINT_001)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POINT_001",
                                      "message": "포인트가 부족합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreatePostResponseDto>> createPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,

            // @Valid: DTO 내부의 @NotNull, @Future, @Min 등 1차 검증 동작
            //   → 실패 시 MethodArgumentNotValidException 발생
            //   → GlobalExceptionHandler가 400 Bad Request로 변환
            @Valid @RequestBody CreatePostRequestDto request
    ) {
        // JWT 토큰에서 검증된 userId 꺼내기
        // 클라이언트가 보낸 헤더가 아니라 토큰 안의 서명된 값 → 위변조 불가
        Long userId = userDetails.getUserId();

        // 명세서: 201 Created 반환
        // ResponseEntity.status(201).body(...) 패턴
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(postCommandService.createPost(userId, request)));
    }

    /**
     * 게시글 목록 조회
     * GET /api/v1/posts?status=OPEN&page=0&size=20
     */
    @Operation(
            summary = "게시글 목록 조회",
            description = """
                    같은 학교 게시글 목록을 조회합니다.
                    - 책임비 포인트 높은 순으로 정렬됩니다. (상위 노출)
                    - 기본 상태: OPEN (모집 중)
                    - status 가능 값: OPEN / MATCHED / COMPLETED / CANCELLED / EXPIRED
                    - 최대 페이지 크기: 50
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
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetPostsItemResponseDto>>> getPosts(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "OPEN")PostStatus status, // defaultValue = "OPEN" → 쿼리스트링 누락 시 OPEN 사용 (명세서 기본값)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,  // size=20 — 누락 시 20, 최대 50
            @RequestParam(defaultValue = "DEPOSIT_DESC") String sort
    ) {
        Long userId = userDetails.getUserId();

        int safeSize = Math.min(size, 50);
        Sort postSort = switch (sort) {
            case "LATEST" -> Sort.by("createdAt").descending();
            case "MEET_AT_ASC" -> Sort.by("meetAt").ascending();
            default -> Sort.by("authorDeposit").descending();
        };

        Pageable pageable = PageRequest.of(
                page,
                safeSize,
                postSort
        );

        return ResponseEntity.ok(
                ApiResponseDto.success(postQueryService.getPosts(userId, status, pageable)));
    }

    /**
     * 게시글 상세 조회
     * GET /api/v1/posts/{postId}
     */
    @Operation(
            summary = "게시글 상세 조회",
            description = """
                    게시글 ID로 단건 상세 정보를 조회합니다.
                    - 같은 학교 게시글만 조회 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다른 학교 게시글 접근 불가 (POST_002)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POST_002",
                                      "message": "다른 학교의 게시글은 조회할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404))
            )
    })
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<GetPostResponseDto>> getPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {
        // JWT 토큰에서 검증된 userId 추출 (클라이언트 위변조 불가)
        Long currentUserId = userDetails.getUserId();
        return ResponseEntity.ok(
                ApiResponseDto.success(postQueryService.getPost(postId, currentUserId)));
    }

    /**
     * 게시글 수정
     * PATCH /api/v1/posts/{postId}
     */
    @Operation(
            summary = "게시글 수정",
            description = """
                    게시글 내용을 수정합니다.
                    - 본인 게시글만 수정 가능합니다.
                    - OPEN 상태일 때만 수정 가능합니다. (매칭 후 수정 불가)
                    - 책임비를 높이면 차액 포인트가 추가 예치됩니다.
                    - 책임비를 낮추면 차액 포인트가 반환됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 게시글이 아님 (POST_005)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_AUTHOR))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "OPEN 상태가 아님 (POST_006)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POST_006",
                                      "message": "OPEN 상태의 게시글만 수정/삭제할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<UpdatePostResponseDto>> updatePost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequestDto request
            ) {
        // JWT에서 검증된 userId 추출
        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(
                ApiResponseDto.success(postCommandService.updatePost(postId, userId, request)));
    }

    /**
     * 게시글 삭제
     * DELETE /api/v1/posts/{postId}
     */
    @Operation(
            summary = "게시글 삭제",
            description = """
                    게시글을 삭제합니다.
                    - 본인 게시글만 삭제 가능합니다.
                    - OPEN 상태일 때만 삭제 가능합니다. (매칭 후 삭제 불가)
                    - 삭제 시 예치 포인트가 전액 반환됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공 - 예치 포인트 전액 반환"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 게시글이 아님 (POST_005)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_AUTHOR))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "OPEN 상태가 아님 (POST_006)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POST_006",
                                      "message": "OPEN 상태의 게시글만 수정/삭제할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<DeletePostResponseDto>> deletePost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(
                ApiResponseDto.success(postCommandService.deletePost(postId, userId)));
    }

    // 내 삭제된 게시글의 삭제 사유 조회
    @Operation(
            summary = "삭제된 게시글 사유 조회",
            description = """
                    관리자에 의해 강제 삭제된 게시글의 사유를 조회합니다.
                    - 본인 게시글만 조회 가능합니다.
                    - 알림 또는 마이페이지에서 진입합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 게시글이 아님 (POST_005)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_AUTHOR))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404))
            )
    })
    @GetMapping("/{postId}/delete-reason")
    public ResponseEntity<ApiResponseDto<DeletedPostReasonResponseDto>> getDeletedPostReason(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(postQueryService.getDeletedPostReason(postId, userId)));
    }
}
