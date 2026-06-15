package com.example.team3final.domain.match.context;

import com.example.team3final.domain.meet.enums.VerificationStatus;

// Meet 도메인이 판정한 Match별 노쇼 결과를 Match 정산 도메인으로 전달한다.
public record NoShowDecision(
        Long matchId,
        VerificationStatus status
) {
}
