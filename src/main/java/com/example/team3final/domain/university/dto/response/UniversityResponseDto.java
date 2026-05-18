package com.example.team3final.domain.university.dto.response;

import com.example.team3final.domain.university.entity.University;
import lombok.Builder;

@Builder // 응답 DTO를 Builder 방식으로 생성할 수 있게 합니다.
public record UniversityResponseDto(
        Long universityId,     // 대학교 ID입니다.
        String universityName, // 대학교 이름입니다.
        String  e_Domain     // 학교 이메일 도메인입니다.
) {
}
