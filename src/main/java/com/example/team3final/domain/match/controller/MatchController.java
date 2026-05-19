package com.example.team3final.domain.match.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.service.MatchCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MatchController {

    private final MatchCommandService matchCommandService;

    /**
     * 매칭 신청 (선착순 매칭 생성)
     *
     * 명세서: MVP 개발에서 내 역할.md - 5.1 createMatch
     *
     * POST /api/v1/posts/{postId}/matches
     */
    @PostMapping("/posts/{postId}/matches")
    public ResponseEntity<ApiResponseDto<CreateMatchResponseDto>> createMatch(
            @RequestHeader("X-User-Id") Long applicantId,
            @PathVariable Long postId
    ) {
        CreateMatchResponseDto response = matchCommandService.createMatch(postId, applicantId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }
}
