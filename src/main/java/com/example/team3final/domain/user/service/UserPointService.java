package com.example.team3final.domain.user.service;

public interface UserPointService {

    // 포인트 차감(예치)
    void deductPoint(Long userId, int amount, Long matchId, String description);
}
