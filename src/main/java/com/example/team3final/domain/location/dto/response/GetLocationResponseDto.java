package com.example.team3final.domain.location.dto.response;

import com.example.team3final.domain.location.dto.LocationDto;

import java.util.List;

// 양측 위치 조회 응답 DTO
public record GetLocationResponseDto (

        LocationDto myLocation,      // 내위치
        LocationDto opponentLocation, // 상대방 위치 (없으면 null) - 기존 프론트 호환용
        List<LocationDto> opponentLocations // 단체 매칭 상대방 위치 목록
) {
    public static GetLocationResponseDto of(LocationDto myLocation, LocationDto opponentLocation) {
        return new GetLocationResponseDto(
                myLocation,
                opponentLocation,
                opponentLocation == null ? List.of() : List.of(opponentLocation)
        );
    }

    public static GetLocationResponseDto of(LocationDto myLocation, List<LocationDto> opponentLocations) {
        List<LocationDto> safeOpponentLocations = opponentLocations == null ? List.of() : opponentLocations;
        return new GetLocationResponseDto(
                myLocation,
                safeOpponentLocations.isEmpty() ? null : safeOpponentLocations.get(0),
                safeOpponentLocations
        );
    }
}
