package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;

import java.util.List;
import java.util.Map;

// User 도메인의 타 도메인 호출용 내부 조회/검증 기능을 제공하는 서비스
public interface UserInternalService {

    // 회원가입 시 가입되어있는 이메일인지 검증
    boolean isEmailAlreadyRegistered(String email);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 이메일로 사용자 ID를 조회합니다.
    Long getUserIdByEmail(String email);

    // userId로 이메일 조회 — WebSocket Principal 매핑용
    String getEmailByUserId(Long userId);

    // 이메일로 User 엔티티 조회(로그인 시 사용)
    User findByEmail(String email);

    // 같은 학교 유저 ID 목록 조회
    List<Long> getUserIdsByUniversityId(Long universityId);

    // AI 매칭 도메인에서 같은 학교의 추천 후보 작성자를 찾을 때 사용합니다.
    List<Long> getActiveUserIdsByUniversityId(Long universityId);

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

    // userId로 User 엔티티를 직접 반환
    User findUserById(Long userId);

    // userId 목록을 닉네임 Map으로 반환 (N+1 방지 배치 조회)
    Map<Long, String> getUserNicknameMap(List<Long> userIds);

    // 두 유저가 같은 학교인지 확인
    boolean isSameUniversity(Long userId, Long otherUserId);

    // 닉네임 검색으로 유저 ID 목록 조회
    List<Long> getUserIdsByNickname(String nickname);
}
