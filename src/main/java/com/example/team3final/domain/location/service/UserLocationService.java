package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;

public interface UserLocationService {

    // 내 위치 업데이트 - 5초마다 호출
    UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto);

    // 양측 위치 조회
    GetLocationResponseDto getLocations(Long matchId, Long userId);

    // 매칭 종료 시 위치 데이터 삭제 (match 도메인에서 호출)
    void deleteLocationsByMatchId(Long matchId);
}
