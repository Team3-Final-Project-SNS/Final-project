package com.example.team3final.domain.user.dto.response;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GetUserResponseDto (
        Long userId,
        String email,
        String name,
        String nickname,
        Long universityId,
        String major,
        String studentNumber,
        LocalDate birthYear,
        Gender gender,
        int point,
        UserStatus status,
        LocalDateTime createdAt

) {
    public static GetUserResponseDto from(User user) {
        return new GetUserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getUniversityId(),
                user.getMajor(),
                user.getStudentNumber(),
                user.getBirthDate(),
                user.getGender(),
                user.getPoint(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
