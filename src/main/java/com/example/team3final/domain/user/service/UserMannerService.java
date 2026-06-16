package com.example.team3final.domain.user.service;

import java.math.BigDecimal;

// User 도메인의 매너온도 조회 및 변경 기능을 담당하는 서비스
public interface UserMannerService {

    // 매너온도 갱신 — 비관락 적용 버전
    // ReviewServiceImpl.createReview() 에서 온도 변동치(temperatureDelta)만 받아 내부에서 가산
    // → 비관락으로 currentTemperature를 읽어 currentTemperature + temperatureDelta 계산 후 저장
    // → 이중 계산 방지: ReviewServiceImpl에서 최종 온도를 계산하지 않고 변동치만 전달
    void updateMannerTemperatureWithLock(Long userId, BigDecimal temperatureDelta);

    // 현재 매너 온도 조회
    BigDecimal getMannerTemperature(Long userId);
}