package com.example.team3final.domain.dispute.util;

// 이의제기 관련 Redis ZSet 키 상수
// MeetRedisZSetKeys와 동일한 패턴
public class DisputeRedisZSetKeys {

    // 이의제기 마감 임박 알림 예약
    // score: holdAt + 23시간 Unix Timestamp / member: disputeId
    public static final String DEADLINE_REMINDER = "dispute:deadline:reminder";

    private DisputeRedisZSetKeys() {} // 인스턴스화 방지
}