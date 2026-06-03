package com.example.team3final.domain.admin.user.dto.response;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminReinstateUserResponseDto(

        Long userId,
        UserStatus status,             // 복구 후 상태(항상 ACTIVE)
        String reason,                 // 복구 사유
        LocalDateTime reinstatedAt     // 복구 처리 시각
) {
    public static AdminReinstateUserResponseDto of(User user, String reason) {
        return new AdminReinstateUserResponseDto(
                user.getId(),
                user.getStatus(),   // reinstate() 호출 후 → ACTIVE
                reason,
                LocalDateTime.now()
        );
    }
}
