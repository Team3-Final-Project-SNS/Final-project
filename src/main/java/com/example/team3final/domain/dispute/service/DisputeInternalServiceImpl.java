package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Dispute 도메인의 타 도메인 호출용 내부 조회 기능을 제공하는 서비스

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeInternalServiceImpl implements DisputeInternalService{

    private final DisputeRepository disputeRepository;

    // 어드민 이의제기 상세 조회용 - disputeId 단건 조회
    @Override
    public Dispute getDisputeById(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));
    }

    @Override
    public Page<Dispute> getDisputesForAdmin(DisputeStatus status, Pageable pageable) {
        // status가 null이면 전체 조회, 있으면 해당 status만 필터링
        if (status == null) {
            return disputeRepository.findAll(pageable);
        }
        return disputeRepository.findAllByStatus(status, pageable);
    }

    // 관리자 - 노쇼 후보군 조회
    // matchId 목록으로 이의제기 존재 여부를 한 번에 조회 (N+1 방지)
    @Override
    public Set<Long> getMatchIdsWithDispute(List<Long> matchIds) {

        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptySet();
        }

        // List -> Set 변환
        return new HashSet<>(disputeRepository.findMatchIdsByMatchIdIn(matchIds));
    }

    @Override
    public Set<Long> getMatchIdsWithActiveDispute(List<Long> matchIds) {

        // matchIds 가 비어있으면 쿼리 날리지 않고 빈 Set 즉시 반환 (불필요한 DB 호출 방지)
        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 관리자가 아직 처리 중인 상태 목록
        List<DisputeStatus> activeStatuses = List.of(
                DisputeStatus.SUBMITTED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.HOLD
        );

        // Repository 쿼리로 조건에 맞는 matchId 목록 조회 후 Set 으로 변환 (중복 제거 + 빠른 contains)
        return new HashSet<>(
                disputeRepository.findMatchIdsByMatchIdInAndStatusIn(matchIds, activeStatuses)
        );
    }
}
