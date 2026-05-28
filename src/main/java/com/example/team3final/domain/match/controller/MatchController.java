package com.example.team3final.domain.match.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CancelMatchResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
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
public class MatchController {

    private final MatchService matchService;
    private final MeetVerificationService meetVerificationService;

    /**
     * 매칭 신청 (선착순 매칭 생성)
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

        CreateMatchResponseDto response = matchService.createMatch(postId, applicantId);
        meetVerificationService.createPendingVerification(response.matchId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    /**
     * 매칭 상세 조회
     * <p>
     * GET /api/v1/matches/{matchId}
     */
    @GetMapping("/matches/{matchId}")
    public ResponseEntity<ApiResponseDto<GetMatchResponseDto>> getMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId
    ) {
        // JWT 토큰에서 검증된 userId 추출 (당사자 검증용)
        Long currentUserId = userDetails.getUserId();

        GetMatchResponseDto response = matchService.getMatch(matchId, currentUserId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    @GetMapping("/matches/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetMatchesResponseDto>>> getMatches(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId();

        int safeSize = Math.min(size, 50);

        // matchedAt 최신순 정렬 (가장 최근 매칭이 위로)
        Pageable pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by("createdAt").descending()
        );

        PageResponseDto<GetMatchesResponseDto> response =
                matchService.getMatches(userId, status, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    /**
     * 매칭 취소
     *
     * PATCH /api/v1/matches/{matchId}/cancel
     */
    @PatchMapping("/matches/{matchId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelMatchResponseDto>> cancelMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody CancelMatchRequestDto request
            ) {
        Long userId = userDetails.getUserId();

        CancelMatchResponseDto response = matchService.cancelMatch(matchId, userId, request);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}

