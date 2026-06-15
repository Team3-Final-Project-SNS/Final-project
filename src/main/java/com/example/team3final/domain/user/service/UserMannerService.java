package com.example.team3final.domain.user.service;

import java.math.BigDecimal;

// User 도메인의 매너온도 조회 및 변경 기능을 담당하는 서비스
public interface UserMannerService {

    // 후기 집계 결과로 매너 온도 재설정
    void updateMannerTemperature(Long userId, BigDecimal mannerTemperature);

    // 매너온도 갱신 — 비관락 적용 버전 (그룹 매칭 동시 후기 제출 시 Lost Update 방지)
    // ReviewServiceImpl.updateAuthorMannerTemperatureByMeetingAverage()에서 호출
    // averageScoreDelta: 이전 평균과 현재 평균의 차이값
    // mannerWeight:      점수 → 온도 변환 가중치 (현재 0.5)
    void updateMannerTemperatureWithLock(Long userId, BigDecimal averageScoreDelta, BigDecimal mannerWeight);

    // 현재 매너 온도 조회
    BigDecimal getMannerTemperature(Long userId);
}
