package com.example.team3final.domain.match.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.service.MatchCommandService;
import com.example.team3final.domain.match.service.MatchQueryService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MatchController {

    private final MatchCommandService matchCommandService;
    private final MatchQueryService matchQueryService;
    private final MeetVerificationService meetVerificationService;

    /**
     * 매칭 신청 (선착순 매칭 생성)
     *
     * 명세서: MVP 개발에서 내 역할.md - 5.1 createMatch
     *
     * POST /api/v1/posts/{postId}/matches
     */
    @PostMapping("/posts/{postId}/matches")
    public ResponseEntity<ApiResponseDto<CreateMatchResponseDto>> createMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {
        // 토큰에서 추출된 검증된 userId
        Long applicantId = userDetails.getUserId();

        CreateMatchResponseDto response = matchCommandService.createMatch(postId, applicantId);
        meetVerificationService.createPendingVerification(response.matchId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    /**
     * 매칭 상세 조회
     *
     * GET /api/v1/matches/{matchId}
     */
    @GetMapping("/matches/{matchId}")
    public ResponseEntity<ApiResponseDto<GetMatchResponseDto>> getMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId
    ) {
        // JWT 토큰에서 검증된 userId 추출 (당사자 검증용)
        Long currentUserId = userDetails.getUserId();

        GetMatchResponseDto response = matchQueryService.getMatch(matchId, currentUserId);

        return  ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
