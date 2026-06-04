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

    // 위치 판정 메서드
    // 기존에는 QR 만료 시 신청자만 노쇼 처리했는데, 만료 시점의 최신 위치를 보고
    // 등록자/신청자/양측 노쇼를 나눠야 함
    boolean isFreshLocationWithinRadius(
            Long matchId,
            Long userId,
            BigDecimal placeLat,
            BigDecimal placeLng,
            double radiusMeters,
            long freshnessSeconds
    );
}
