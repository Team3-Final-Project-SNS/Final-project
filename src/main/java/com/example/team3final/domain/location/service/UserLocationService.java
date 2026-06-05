package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;

import java.math.BigDecimal;

public interface UserLocationService {

    // 내 위치 업데이트
    UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto);

    // 양측 위치 조회
    GetLocationResponseDto getLocations(Long matchId, Long userId);

    // QR 만료 시 노쇼 판정을 위해 호출되는 메서드
    boolean isFreshLocationWithinRadius(
            Long matchId,
            Long userId,
            BigDecimal placeLat,
            BigDecimal placeLng,
            double radiusMeters,
            long freshnessSeconds
    );
}
