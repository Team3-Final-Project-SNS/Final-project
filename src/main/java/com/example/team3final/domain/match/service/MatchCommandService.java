package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;

public interface MatchCommandService {

    /**
     * 매칭 신청 / 생성 (선착순)
     *
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출 후 전달)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);

    /**
     * 매칭 완료 처리 — 도메인 간 호출용
     *
     * 사용처:
     * - 만남 인증(정): QR 인증 성공 시 호출
     *
     * 처리하는 후속 작업 (오케스트레이션):
     * 1) Match.status → COMPLETED, completedAt 기록
     * 2) Post.status → COMPLETED
     * 3) ChatRoom.isActive → false (나가기 가능 상태로 전환)
     * 4) TODO: 양측 포인트 환불 (User 도메인 머지 후)
     *
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    void completeMatch(Long matchId);

    void markNoShow(Long matchId, MatchStatus noShowStatus);
}
