package com.example.team3final.domain.meet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExtensionStatus {

    NONE("요청 없음"),       // 초기 상태 — 연장 요청 없음
    REQUESTED("요청 접수"),  // 연장 요청 접수 — 상대방 응답 대기 중
    ACCEPTED("요청 완료"),   // 연장 수락 완료 — 약속 시간 15분 연장 확정
    REJECTED("요청 거절"),   // 연장 거절 — 재요청 가능
    EXPIRED("요청 만료");     // 연장 요청 만료 — 5분 내 응답 없음, 재요청 가능

    private final String description;
}
