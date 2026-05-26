package com.example.team3final.domain.dispute.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DisputeStatus {

    SUBMITTED("제출 완료, 검토 대기"),
    UNDER_REVIEW("관리자 검토중"),
    ACCEPTED("수용"),                // 노쇼 취소, 예치금 반환
    PARTIALLY_ACCEPTED("부분 수용"),
    REJECTED("기각");                // 노쇼 확정, 예치금 환수

    private final String description;

    // 이미 종결된 상태인지 여부
    public boolean isClosed() {
        // SUBMITTED, UNDER_REVIEW 가 아니면 모두 종결 상태로 본다
        return this == ACCEPTED || this == PARTIALLY_ACCEPTED || this == REJECTED;
    }
}
