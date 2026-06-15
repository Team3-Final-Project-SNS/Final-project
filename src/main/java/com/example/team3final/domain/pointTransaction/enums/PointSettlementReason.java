package com.example.team3final.domain.pointTransaction.enums;

// 최종 정산 대상 책임비를 구분한다. 최종 환급/부분 환급/패널티 거래에만 저장한다.
public enum PointSettlementReason {
    APPLICANT_DEPOSIT,
    AUTHOR_DEPOSIT
}
