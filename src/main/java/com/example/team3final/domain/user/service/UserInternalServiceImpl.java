package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// User 도메인의 타 도메인 호출용 내부 조회/검증 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInternalServiceImpl implements UserInternalService {

    private final UserRepository userRepository;

    // 회원가입 시 가입되어있는 이메일인지 검증
    @Override
    public boolean isEmailAlreadyRegistered(String email) {
        return userRepository.existsByEmailAndStatusNot(email, UserStatus.WITHDRAWN);
    }

    // 닉네임 중복확인
    @Override
    public boolean existsByNickname(String nickname) {
        // UserRepository를 통해 닉네임과 학교 ID가 동시에 일치하는 유저가 있는지 확인합니다.
        return userRepository.existsByNickname(nickname);
    }

    // 이메일로 사용자 ID를 조회합니다.
    @Override
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }

    // userId로 이메일 조회 — WebSocket Principal 매핑용
    @Override
    public String getEmailByUserId(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND))
                .getEmail();
    }

    // 이메일로 User 엔티티 조회(로그인 시 사용)
    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    // 같은 학교 활성 사용자 ID 목록 조회
    @Override
    public List<Long> getUserIdsByUniversityId(Long universityId) {
        // UserRepository에서 universityId 기준으로 ACTIVE 유저 ID만 조회
        // 탈퇴/정지 유저 제외 → 게시글 목록에 노출되면 안 되는 유저 자동 필터링
        return userRepository.findIdsByUniversityId(universityId);
    }

    // AI 매칭 도메인에서 같은 학교의 활성화 사용자 ID 조회
    @Override
    public List<Long> getActiveUserIdsByUniversityId(Long universityId) {
        // AI 매칭 도메인이 UserRepository를 직접 참조하지 않도록,
        // 같은 학교의 ACTIVE 사용자 ID 조회는 User 도메인 서비스가 담당합니다.
        return userRepository.findActiveUserIdsByUniversityId(universityId);
    }

    // User의 정보 중 유저ID, 닉네임, 학과, 학번, 학교ID만 참조
    @Override
    public Map<Long, UserInfoDto> getUserInfos(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // DB에서 IN 절로 유저들을 한 번에(Bulk) 땡겨와서 Map으로 반환
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserInfoDto::from));
    }

    // userId로 User 엔티티 직접 반환
    @Override
    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    // userId 목록을 닉네임 Map으로 반환
    @Override
    public Map<Long, String> getUserNicknameMap(List<Long> userIds) {

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    // 두 사용자가 같은 학교소속인지 확인
    @Override
    public boolean isSameUniversity(Long userId, Long otherUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return user.getUniversityId().equals(otherUser.getUniversityId());
    }

    // 닉네임 검색으로 유저 ID 목록 조회
    @Override
    public List<Long> getUserIdsByNickname(String nickname) {
        return userRepository.findIdsByNicknameLike(nickname);
    }
}
