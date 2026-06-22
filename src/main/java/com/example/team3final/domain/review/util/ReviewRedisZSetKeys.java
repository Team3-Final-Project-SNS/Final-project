package com.example.team3final.domain.review.util;

// 후기 관련 Redis ZSet 키 상수
public class ReviewRedisZSetKeys {

    // 후기 작성 마지막 날 알림 예약
    // score: 알림 발송 시각 Unix Timestamp / member: matchId
    public static final String DEADLINE_REMINDER = "review:deadline:reminder";

    private ReviewRedisZSetKeys() {} // 인스턴스화 방지
}