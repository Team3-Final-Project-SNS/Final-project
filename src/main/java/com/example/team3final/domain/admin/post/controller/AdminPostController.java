package com.example.team3final.domain.admin.post.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.service.AdminPostService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
