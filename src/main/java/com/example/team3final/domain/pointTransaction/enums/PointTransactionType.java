package com.example.team3final.domain.pointTransaction.enums;

public enum PointTransactionType {

    JOIN_BONUS("회원가입 보너스 지급"),          // 가입 시 10,000P 지급
    DEPOSIT("책임비 포인트 예치"),             // 게시글 작성 또는 매칭 신청 시 포인트 차감
    EDIT_DEPOSIT("책임비 포인트 변경"),        // 게시글 수정으로 예치 포인트가 변경된 경우
    REFUND("포인트 전액 반환"),                // 정상 완료 또는 취소로 전액 반환
    PARTIAL_REFUND("포인트 일부 반환"),        // 매칭 취소 시 50% 반환
    PENALTY("패널티 포인트 차감");             // 노쇼 등으로 예치 포인트 차감

    private final String description;

    PointTransactionType(String description) {
        this.description = description;
    }
}