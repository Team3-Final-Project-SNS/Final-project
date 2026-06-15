package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.dto.response.AdminUserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// User 도메인의 관리자 조회/제재 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserModerationServiceImpl implements UserModerationService {

    private final UserRepository userRepository;

    // Admin 도메인에서 사용할 유저 목록 조회
    @Override
    public Page<User> getUsersForAdmin(UserStatus status, String keyword, Pageable pageable) {
        return userRepository.findAllByForAdmin(status, keyword, pageable);
    }

    // email, university를 포함한 관리자용 단건 조회
    @Override
    public AdminUserInfoDto getAdminUserInfo(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return AdminUserInfoDto.from(user);
    }

    // Admin 도메인에서 사용할 유저 계정 정지
    // days: 정지 일수 (null = 영구정지)
    @Override
    @Transactional
    public void suspendUser(Long userId, Integer days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 계정이 정지 중인지 확인
        if (user.getStatus() == UserStatus.SUSPENDED) {
            boolean isSuspensionExpired = user.getSuspendedUntil() != null
                    && LocalDateTime.now().isAfter(user.getSuspendedUntil());

            if (isSuspensionExpired) {
                // 정지 기간이 만료됨 → 자동 복구 후 새 정지 처리
                user.reinstate();
            } else {
                // 아직 유효한 정지 또는 영구 정지 → 중복 정지 시도 예외
                throw new UserException(ErrorCode.ADMIN_USER_ALREADY_SUSPENDED);
            }
        }

        // 더티체킹으로 자동 업데이트
        user.suspend(days);
    }

    // 신고 박탈 처리 메서드
    @Override
    @Transactional
    public void banReportFeature(Long userId, int days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        user.banReport(days);
    }

    // 박탈 여부 체크 로직
    @Override
    public boolean isReportBanned(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        return user.isReportBanned();
    }
}
