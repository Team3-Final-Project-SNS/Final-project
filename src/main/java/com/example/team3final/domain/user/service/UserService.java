package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface UserService {

    // 이메일로 사용자 ID를 조회합니다.
    // 다른 도메인에서 로그인 사용자의 userId가 필요할 때 사용합니다.
    Long getUserIdByEmail(String email);

    // 회원가입 시 가입되어있는 이메일인지 검증
    boolean isEmailAlreadyRegistered(String email);

    // 이메일로 User 엔티티 조회(로그인시 사용)
    User findByEmail(String email);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 회원가입 완료 후 User에 저장하기
    User createUser(String email, String encodedPassword, String name, String nickname,
                    Long universityId, String major, String studentNumber,
                    LocalDate birthDate, Gender gender);

    // 내 정보 조회
    GetUserResponseDto getUser(Long userId);

    // 내 정보 수정
    UpdateUserResponseDto updateUser(Long userId, UpdateUserRequestDto request);

    // User 엔티티 대신 DTO 반환 — 도메인 간 호출 전용
    UserInfoDto getUserInfo(Long userId);

    // User 정보 일괄 조회 - 도메인 간 호출용
    // 한 번의 IN 쿼리로 가져와 N+1 문제 방지
    Map<Long, UserInfoDto> getUserInfos(List<Long> userIds);
    // Admin 유저 목록 조회용
    Page<User> getUsersForAdmin(UserStatus status, String keyword, Pageable pageable);

    // Admin 유저 계정 정지
    void suspendUser(Long userId);

    // Admin 도메인에서 사용할 userId 목록을 닉네임 Map으로 반환 (N+1 방지 배치 조회)
    Map<Long, String> getUserNicknameMap(List<Long> userIds);
}
