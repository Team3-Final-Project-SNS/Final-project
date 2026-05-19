package com.example.team3final.domain.location.dto;

import com.example.team3final.domain.location.entity.UserLocation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 단일 위치 정보 DTO (내 위치 or 상대방 위치)
public record LocationDto (
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime updatedAt
) {
    public static LocationDto from(UserLocation userLocation) {
        return new LocationDto(
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                userLocation.getUpdatedAt()
        );
    }
}
