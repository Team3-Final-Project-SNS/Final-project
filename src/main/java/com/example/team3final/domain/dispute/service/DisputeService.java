package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface DisputeService {

    /**
     * 이의제기 제출.
     * @param matchId 대상 매칭 ID (URL path)
     * @param userId  요청자 ID (인증 토큰에서 추출)
     * @param request 사유
     */
    CreateDisputeResponseDto createDispute(Long matchId, Long userId, CreateDisputeRequestDto request);

    /**
     * 내가 제출한 이의제기 상태 조회.
     */
    DisputeResponseDto getDispute(Long matchId, Long userId);

    // 어드민 이의제기 상세 조회용 - disputeId 단건 조회
    Dispute getDisputeById(Long disputeId);

    // 어드민 목록 조회용 — status null이면 전체 조회
    Page<Dispute> getDisputesForAdmin(DisputeStatus status, Pageable pageable);

    // 노쇼 후보군 조회용
    Set<Long> getMatchIdsWithDispute(List<Long> matchIds);
}
