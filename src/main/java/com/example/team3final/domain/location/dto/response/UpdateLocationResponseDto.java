package com.example.team3final.domain.location.dto.response;

import com.example.team3final.domain.location.entity.UserLocation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateLocationResponseDto (

        Long matchId,
        Long userId,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime updatedAt
) {
    public static UpdateLocationResponseDto from(UserLocation userLocation) {
        return new UpdateLocationResponseDto(
                userLocation.getMatchId(),
                userLocation.getUserId(),
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                userLocation.getUpdatedAt()
        );
    }
}
