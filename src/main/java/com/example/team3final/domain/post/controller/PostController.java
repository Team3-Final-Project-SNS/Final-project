package com.example.team3final.domain.post.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostCommandService;
import com.example.team3final.domain.post.service.PostQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            // ⚠️ 임시: 인증 모듈 완성 전까지 헤더로 userId 전달
            // 인증 완료 후 @AuthenticationPrincipal 또는 커스텀 Argument Resolver로 교체
            // (인증 담당자와 합의된 헤더 이름: X-User-Id)
            @RequestHeader("X-User-Id") Long userId,

            // @Valid: DTO 내부의 @NotNull, @Future, @Min 등 1차 검증 동작
            //   → 실패 시 MethodArgumentNotValidException 발생
            //   → GlobalExceptionHandler가 400 Bad Request로 변환
            @Valid @RequestBody CreatePostRequestDto request
    ) {
        // Service에 위임 — Controller는 로직 X
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
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "OPEN")PostStatus status, // defaultValue = "OPEN" → 쿼리스트링 누락 시 OPEN 사용 (명세서 기본값)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size  // size=20 — 누락 시 20, 최대 50
    ) {
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
}
