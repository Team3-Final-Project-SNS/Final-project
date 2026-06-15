package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

// Dispute 도메인의 내부 조회 기능을 다른 도메인에 제공하는 서비스
public interface DisputeInternalService {

    // 어드민 이의제기 상세 조회용 - disputeId 단건 조회
    Dispute getDisputeById(Long disputeId);

    // 어드민 목록 조회용 — status null이면 전체 조회
    Page<Dispute> getDisputesForAdmin(
            DisputeStatus status,
            Pageable pageable
    );

    // 노쇼 후보군 조회용
    Set<Long> getMatchIdsWithDispute(List<Long> matchIds);

    // 노쇼 확정 배치(judgeNoShowConfirmed)에서 사용
    Set<Long> getMatchIdsWithActiveDispute(List<Long> matchIds);
}
