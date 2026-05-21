package com.example.team3final.domain.user.service;

public interface UserPointService {

    // 포인트 차감(예치)
    void deductPoint(Long userId, int amount, Long matchId);

    // 포인트 전액 환급
    void refundPoint(Long userId, int amount, Long matchId);
}
