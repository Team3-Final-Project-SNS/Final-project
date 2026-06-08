package com.example.team3final.common.utils;

// Redis Lua Script 상수
// 조회 + 삭제를 원자적으로 처리 → 분산 환경에서 중복 처리 방지
public class RedisLuaScripts {

    // ZSet에서 현재 시각 이전 항목 조회 후 즉시 삭제
    // KEYS[1] = ZSet 키
    // ARGV[1] = 현재 Unix Timestamp (score 기준)
    // 반환값: 처리 대상 member 목록 (matchId, chatRoomId 등)
    public static final String POP_READY_ITEMS = """
            local items = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1])
            if #items > 0 then
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
            end
            return items
            """;

    private RedisLuaScripts() {} // 인스턴스화 방지
}