package com.example.team3final.domain.pointTransaction.enums;

import lombok.Getter;

@Getter
public enum PointTransactionType {

    JOIN_BONUS("회원가입 보너스 지급"),         // 가입 시 10,000P 지급
    CHARGE("유료 포인트 충전"),                    // 결제(현금)로 포인트 충전 -> 잔액 증가
    CHARGE_CANCELLED("유료 포인트 회수"),      // 결제 취소로 paid_point 회수
    DEPOSIT("책임비 포인트 예치"),             // 게시글 작성 또는 매칭 신청 시 포인트 차감
    EDIT_DEPOSIT("책임비 포인트 변경"),        // 게시글 수정으로 예치 포인트가 변경된 경우
    REFUND("포인트 전액 반환"),                // 정상 완료 또는 취소로 전액 반환
    PARTIAL_REFUND("포인트 일부 반환"),        // 매칭 취소 시 50% 반환
    PENALTY("패널티 포인트 차감"),             // 노쇼 등으로 예치 포인트 차감
    REPORT_REWARD("신고 채택 포상 지급"),      // 신고 채택 시 50P 지급
    REVIEW_REWARD("후기 작성 포상 지급");      // 후기 작성 시 50P 지급

    private final String description;

    PointTransactionType(String description) {
        this.description = description;
    }
}
