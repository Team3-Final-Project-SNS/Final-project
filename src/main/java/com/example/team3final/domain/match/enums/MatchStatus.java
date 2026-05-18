package com.example.team3final.domain.match.enums;

public enum MatchStatus {

    // 매칭 확정 - 채팅방 생성, 약속 시간 대기중
    MATCHED,

    // 만남 정상 완료 - QR 인증까지 모두 완료
    COMPLETED,

    // 매칭 취소 - 약속 시간 이전 취소 (취소자 50% 반환)
    CANCELLED,

    // 등록자 노쇼
    AUTHOR_NO_SHOW,

    // 신청자 노쇼
    APPLICANT_NO_SHOW,

    // 양측 노쇼
    BOTH_NO_SHOW,

    // 이의 제기중 (관리자 검토 대기)
    DISPUTED
}
