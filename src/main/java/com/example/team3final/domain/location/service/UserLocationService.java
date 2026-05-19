package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;

public interface UserLocationService {

    // 내 위치 업데이트 - 5초마다 호출
    UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto);
}
