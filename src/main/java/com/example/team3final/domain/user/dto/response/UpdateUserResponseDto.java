package com.example.team3final.domain.user.dto.response;

import com.example.team3final.domain.user.entity.User;

import java.time.LocalDateTime;

public record UpdateUserResponseDto (
        Long userId,
        String nickname,
        String major,
        boolean passwordChanged,
        LocalDateTime updatedAt
) {
    public static UpdateUserResponseDto from(User user, boolean passwordChanged) {
        return new UpdateUserResponseDto(
                user.getId(),
                user.getNickname(),
                user.getMajor(),
                passwordChanged,
                user.getUpdatedAt()
        );
    }

}
