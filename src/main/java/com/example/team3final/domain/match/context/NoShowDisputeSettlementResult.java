package com.example.team3final.domain.match.context;

import java.util.List;

// 이의제기 판정에서 실제 처리된 Match와 이의제기자 환급액을 함께 전달한다.
public record NoShowDisputeSettlementResult(
        Long postId,
        List<Long> processedMatchIds,
        int refundedPoint
) {
    public static NoShowDisputeSettlementResult empty(Long postId) {
        return new NoShowDisputeSettlementResult(postId, List.of(), 0);
    }
}
