package com.example.team3final.domain.user.dto.response;

import java.time.LocalDateTime;

public record WithdrawResponseDto (
        Long userId,                 // 탈퇴한 유저 ID
        LocalDateTime withdrawnAt    // 탈퇴 처리 시작
) {
        public static WithdrawResponseDto from(Long userId) {
        return new WithdrawResponseDto(userId, LocalDateTime.now());
    }
}
