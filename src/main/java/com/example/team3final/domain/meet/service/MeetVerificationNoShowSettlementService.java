package com.example.team3final.domain.meet.service;

import java.util.List;

// 같은 Post의 노쇼 확정과 포인트 정산을 독립 트랜잭션으로 처리한다.
public interface MeetVerificationNoShowSettlementService {

    void settlePost(Long postId, List<Long> candidateMatchIds);
}
