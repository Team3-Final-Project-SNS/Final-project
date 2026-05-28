package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
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

    // 1. 구현 클래스에서 반드시 구현해야 하는 bulk 조회 메서드
    Map<Long, UserInfoDto> getUserInfos(List<Long> userIds);

    // 2. bulk 조회를 재사용하는 단건 default 메서드 (공통 로직으로 합침)
    default UserInfoDto getUserInfo(Long userId) {
        if (userId == null) {
            throw new UserException(ErrorCode.USER_NOT_FOUND);
        }

        // bulk 메서드 호출 후 Map에서 결과 추출
        UserInfoDto info = getUserInfos(List.of(userId)).get(userId);

        if (info == null) {
            throw new UserException(ErrorCode.USER_NOT_FOUND);
        }
        return info;
    }

    // Admin 유저 목록 조회용
    Page<User> getUsersForAdmin(UserStatus status, String keyword, Pageable pageable);

    // Admin 유저 계정 정지
    // days: 정지 일수 (null = 영구정지)
    void suspendUser(Long userId, Integer days);

    // Admin 도메인에서 사용할 userId 목록을 닉네임 Map으로 반환 (N+1 방지 배치 조회)
    Map<Long, String> getUserNicknameMap(List<Long> userIds);

    // 회원 탈퇴 처리 - 비밀번호 검증 후 상태를 Withdrawn으로 변경
    void withdrawUser(Long userId, String rawPassword);
}
