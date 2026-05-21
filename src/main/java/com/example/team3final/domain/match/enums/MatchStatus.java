package com.example.team3final.domain.match.enums;

import lombok.Getter;

@Getter
public enum MatchStatus {

    MATCHED("매칭 완료"),             //채팅방 생성, 약속 시간 대기 중
    COMPLETED("만남 완료"),           // QR 인증까지 모두 완료
    CANCELLED("매칭 취소"),           // 약속 시간 이전 취소 (취소자 50% 반환)
    AUTHOR_NO_SHOW("등록자 노쇼"),    // 등록자가 안 나타남
    APPLICANT_NO_SHOW("신청자 노쇼"), // 신청자가 안 나타남
    BOTH_NO_SHOW("양측 노쇼"),        // 둘 다 안 나타남
    DISPUTED("이의 제기 중");         // 관리자 검토 대기

    private final String description;

    MatchStatus(String description) {
        this.description = description;
    }
}
