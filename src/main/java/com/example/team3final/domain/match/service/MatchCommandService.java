package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CancelMatchResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;

// Match 도메인의 생성/취소 등 사용자 요청 기반 변경 작업을 담당하는 서비스
public interface MatchCommandService {

    /**
     * 매칭 신청 / 생성 (선착순)
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);

    /**
     * 매칭 취소 — Controller 직접 호출 (명세서 5.3)
     * @param matchId 취소할 매칭 ID
     * @param userId  취소 요청자 ID (당사자 검증 + 50%/100% 구분)
     * @param request 취소 요청 DTO
     * @throws MatchException MATCH_001/002/006/007
     */
    CancelMatchResponseDto cancelMatch(
            Long matchId,
            Long userId,
            CancelMatchRequestDto request);
}
