package com.example.team3final.domain.match.service;

import java.util.List;

// 실제 상태 전환과 정산이 끝난 Match만 MeetVerification 확정/알림 대상으로 돌려준다.
public record NoShowSettlementResult(
        Long postId,
        List<Long> processedMatchIds
) {
    public static NoShowSettlementResult empty(Long postId) {
        return new NoShowSettlementResult(postId, List.of());
    }
}
