package com.example.team3final.domain.user.dto.response;

import com.example.team3final.domain.user.entity.User;

// 도메인 간 호출 전용 DTO — User 엔티티 직접 노출 방지
public record UserInfoDto(
        Long userId,
        String nickname,
        String major,
        String studentNumber,
        Long universityId
) {
    public static UserInfoDto from(User user) {
        return new UserInfoDto(
                user.getId(),
                user.getNickname(),
                user.getMajor(),
                user.getStudentNumber(),
                user.getUniversityId()
        );
    }
}
