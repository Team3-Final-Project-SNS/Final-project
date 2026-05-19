package com.example.team3final.domain.location.dto.response;

import com.example.team3final.domain.location.dto.LocationDto;

// 양측 위치 조회 응답 DTO
public record GetLocationResponseDto (

        LocationDto myLocation,      // 내위치
        LocationDto opponentLocation // 상대방 위치 (없으면 null)
) {
    public static GetLocationResponseDto of(LocationDto myLocation, LocationDto opponentLocation) {
        return new GetLocationResponseDto(myLocation, opponentLocation);
    }
}
