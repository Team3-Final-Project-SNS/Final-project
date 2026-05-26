package com.example.team3final.domain.admin.user.dto.response;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminGetUsersResponseDto(

        Long userId,
        String email,
        String name,
        String nickname,
        String universityName,
        int point,
        double mannerTemperature,
        UserStatus status,
        LocalDateTime createdAt
) {
    public static AdminGetUsersResponseDto of(User user, String universityName, double mannerTemperature) {
        return new AdminGetUsersResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                universityName,
                user.getPoint(),
                mannerTemperature,
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
