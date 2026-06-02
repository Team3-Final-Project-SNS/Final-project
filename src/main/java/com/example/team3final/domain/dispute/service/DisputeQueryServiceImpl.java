package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeQueryServiceImpl implements DisputeQueryService {

    private final DisputeRepository disputeRepository;

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
