package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;

import java.time.LocalDate;

// User 도메인의 회원 생성/조회/수정/탈퇴 등 사용자 요청 기반 기능을 담당하는 서비스
public interface UserCommandService {

    // 회원가입 완료 후 User에 저장하기
    User createUser(String email, String encodedPassword, String name, String nickname,
                    Long universityId, String major, String studentNumber,
                    LocalDate birthDate, Gender gender);

    // 내 정보 조회
    GetUserResponseDto getUser(Long userId);

    // 내 정보 수정
    UpdateUserResponseDto updateUser(Long userId, UpdateUserRequestDto request);

    // 회원 탈퇴 처리 - 비밀번호 검증 후 상태를 Withdrawn으로 변경
    void withdrawUser(Long userId, String rawPassword);
}
