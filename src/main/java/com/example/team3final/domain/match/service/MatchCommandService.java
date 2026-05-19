package com.example.team3final.domain.match.service;

import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;

public interface MatchCommandService {

    /**
     * 매칭 신청 / 생성 (선착순)
     *
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출 후 전달)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);
}
