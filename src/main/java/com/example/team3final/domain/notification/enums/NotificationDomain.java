package com.example.team3final.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationDomain {

    MATCH("매칭"),      // 매칭 관련 알림
    MEET("만남"),       // 만남 인증/연장 관련 알림
    CHAT("채팅"),       // 채팅 관련 알림
    POINT("포인트"),    // 포인트 변동 알림
    REPORT("신고"),     // 신고 처리 알림
    DISPUTE("이의제기"), // 이의제기 관련 알림
    INQUIRY("문의"),    // 고객 문의 알림
    SYSTEM("시스템");   // 시스템 공지

    private final String description;

}
