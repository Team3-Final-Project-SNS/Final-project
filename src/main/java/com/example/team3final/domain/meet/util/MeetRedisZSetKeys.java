package com.example.team3final.domain.meet.util;

// 만남 관련 Redis ZSet 키 상수
public class MeetRedisZSetKeys {

    // 만남 30분 전 알림 예약
    // score: 알림 발송 시각 Unix Timestamp / member: matchId
    public static final String REMINDER_30 = "meet:reminder:30";

    // 만남 15분 전 알림 예약
    public static final String REMINDER_15 = "meet:reminder:15";

    // 만남 임박 알림 예약 (5분 전)
    public static final String REMINDER_IMMINENT = "meet:reminder:imminent";

    // 만남 연장 타임아웃 예약
    // score: 타임아웃 시각 Unix Timestamp / member: meetVerificationId
    public static final String EXTENSION_TIMEOUT = "meet:extension:timeout";

    private MeetRedisZSetKeys() {} // 인스턴스화 방지
}