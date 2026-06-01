package com.example.team3final.domain.user.dto.response;

import com.example.team3final.domain.user.entity.User;

public record AdminUserInfoDto (

        Long userId,
        String nickname,
        String email,        // 관리자 상세 조회에서만 노출
        Long universityId    // universityName 조회에 사용
) {
    public static AdminUserInfoDto from(User user) {
        return new AdminUserInfoDto(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getUniversityId()
        );
    }
}
