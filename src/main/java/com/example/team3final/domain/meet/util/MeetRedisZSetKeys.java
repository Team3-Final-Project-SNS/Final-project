package com.example.team3final.domain.meet.util;

// 만남 관련 Redis ZSet 키 상수
public class MeetRedisZSetKeys {

    // ── 만남 시간 알림 — HOST (등록자) ──────────────────────────────────
    // member: postId
    // 같은 postId는 ZSet에서 중복 불가 → 신청자가 여러 명이어도 1건만 유지
    public static final String REMINDER_30_HOST       = "meet:reminder:30:host";
    public static final String REMINDER_15_HOST       = "meet:reminder:15:host";
    public static final String REMINDER_IMMINENT_HOST = "meet:reminder:imminent:host";
    public static final String REMINDER_OVERDUE_HOST  = "meet:reminder:overdue:host";

    // ── 만남 시간 알림 — GUEST (신청자) ─────────────────────────────────
    // member: matchId
    // 신청자별 개별 예약 → 각자 1번씩 수신
    public static final String REMINDER_30_GUEST       = "meet:reminder:30:guest";
    public static final String REMINDER_15_GUEST       = "meet:reminder:15:guest";
    public static final String REMINDER_IMMINENT_GUEST = "meet:reminder:imminent:guest";
    public static final String REMINDER_OVERDUE_GUEST  = "meet:reminder:overdue:guest";

    // ── 만남 연장 타임아웃 ───────────────────────────────────────────────
    // score: 타임아웃 시각 Unix Timestamp / member: meetVerificationId
    public static final String EXTENSION_TIMEOUT = "meet:extension:timeout";

    private MeetRedisZSetKeys() {} // 인스턴스화 방지
}