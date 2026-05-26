package com.example.team3final.domain.admin.user.dto.response;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminSuspendUserResponseDto (

        Long userId,
        UserStatus status,
        String reason,
        LocalDateTime suspendedAt
) {
    public static AdminSuspendUserResponseDto of(User user, String reason) {
        return new AdminSuspendUserResponseDto(
                user.getId(),
                user.getStatus(),
                reason,
                LocalDateTime.now() // 정지 처리 시각
        );
    }
}
