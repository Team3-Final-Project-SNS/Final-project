package com.example.team3final.domain.admin.post.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostsResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminRestorePostResponseDto;
import com.example.team3final.domain.admin.post.service.AdminPostService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.post.enums.PostStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminPostController {

    private final AdminPostService adminPostService;

    // 게시글 강제 삭제
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponseDto<AdminDeletePostResponseDto>> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @Valid @RequestBody AdminDeletePostRequestDto requestDto) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminPostService.deletePost(adminId, postId, requestDto)));
    }

    // 관리자 게시글 목록 조회
    @GetMapping("/posts")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminGetPostsResponseDto>>> getPosts(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String authorNickname,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long adminId = adminDetails.getAdminId();
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                ApiResponseDto.success(adminPostService.getPosts(adminId, universityId, authorNickname, status, deleted, keyword, pageable)));
    }

    // 관리자 게시글 상세 조회
    @GetMapping("/posts/{postId}")
    public ResponseEntity<ApiResponseDto<AdminGetPostResponseDto>> getPost(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long postId) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminPostService.getPost(adminId, postId)));
    }

    // 강제 삭제 게시글 복구
    @PostMapping("/posts/{postId}/restore")
    public ResponseEntity<ApiResponseDto<AdminRestorePostResponseDto>> restorePost(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long postId) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminPostService.restorePost(adminId, postId)));
    }
}
