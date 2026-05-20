package com.example.team3final.domain.post.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostCommandService;
import com.example.team3final.domain.post.service.PostQueryService;
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
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;

    /**
     * 게시글 작성
     * 명세서: MVP 개발에서 내 역할.md - 4.1 createPost
     *
     * POST /api/v1/posts
     */
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

        CreatePostResponseDto response = postCommandService.createPost(userId, request);

        // 명세서: 201 Created 반환
        // ResponseEntity.status(201).body(...) 패턴
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    /**
     * 게시글 목록 조회
     * 명세서: MVP 개발에서 내 역할.md - 4.2 getPosts
     *
     * GET /api/v1/posts?status=OPEN&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetPostsItemResponseDto>>> getPosts(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "OPEN")PostStatus status, // defaultValue = "OPEN" → 쿼리스트링 누락 시 OPEN 사용 (명세서 기본값)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size  // size=20 — 누락 시 20, 최대 50
    ) {
        Long userId = userDetails.getUserId();

        int safeSize = Math.min(size, 50);

        Pageable pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by("authorDeposit").descending()
        );

        PageResponseDto<GetPostsItemResponseDto> response =
                postQueryService.getPosts(userId, status, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    /**
     * 게시글 상세 조회
     * 명세서: MVP 개발에서 내 역할.md - 4.3 getPost
     *
     * GET /api/v1/posts/{postId}
     */
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<GetPostResponseDto>> getPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {
        // JWT 토큰에서 검증된 userId 추출 (클라이언트 위변조 불가)
        Long currentUserId = userDetails.getUserId();

        // Service 호출 - 검증/조회/조립 모두 위임
        GetPostResponseDto response = postQueryService.getPost(postId, currentUserId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    /**
     * 게시글 수정
     * 명세서: MVP 개발에서 내 역할.md - 4.4 updatePost
     *
     * PATCH /api/v1/posts/{postId}
     */
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<UpdatePostResponseDto>> updatePost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequestDto request
            ) {
        // JWT에서 검증된 userId 추출
        Long userId = userDetails.getUserId();

        // Service에 위임 - 검증/차액처리/업데이트 모두 위임
        UpdatePostResponseDto response = postCommandService.updatePost(postId, userId, request);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
