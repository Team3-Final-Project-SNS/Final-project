package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserLocationService {

    // 내 위치 업데이트
    UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto);

    // 양측 또는 그룹 참여자 위치 조회
    GetLocationResponseDto getLocations(Long matchId, Long userId);

    // QR 만료 시점에 사용자가 현재 약속 장소 반경 안에 있는지 판단한다.
    // 조건:
    // 1. 위치 데이터가 존재해야 함
    // 2. 최근 freshnessSeconds 이내 업데이트여야 함
    // 3. 약속 장소 반경 radiusMeters 안이어야 함
    boolean isFreshLocationWithinRadius(
            Long matchId,
            Long userId,
            BigDecimal placeLat,
            BigDecimal placeLng,
            double radiusMeters,
            long freshnessSeconds
    );

    // QR 만료 시 양측 모두 현재 반경 밖에 있는 경우,
    // 둘 중 누가 먼저 반경을 벗어났는지 판단
    // 정책 -> 먼저 벗어난 사람만 노쇼 예정 처리
    Optional<Long> findFirstLeftUserId(Long matchId, Long authorId, Long applicantId);
}
