package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.enums.MatchStatus;

public record CancelMatchResponseDto(
        Long matchId,
        MatchStatus status,
        int refundedPoint,  // 취소자 반환액 (50%)
        int forfeitedPoint  // 취소자 몰수액 (50%)
) {
    /**
     * 매칭 ID + 상태 + 취소자 환불/몰수액으로 응답 생성
     *
     * @param matchId        취소된 매칭 ID
     * @param status         변경된 상태 (CANCELLED)
     * @param refundedPoint  취소자 반환 포인트 (예치금의 50%)
     * @param forfeitedPoint 취소자 몰수 포인트 (예치금의 50%)
     */
    public static CancelMatchResponseDto of(
            Long matchId,
            MatchStatus status,
            int refundedPoint,
            int forfeitedPoint
    ) {
        return new CancelMatchResponseDto(matchId, status, refundedPoint, forfeitedPoint);
    }
}
