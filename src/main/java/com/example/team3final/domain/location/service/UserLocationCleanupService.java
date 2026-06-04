package com.example.team3final.domain.location.service;

public interface UserLocationCleanupService {

    // 위치 정보 삭제
    void deleteLocationsByMatchId(Long matchId);
}
