package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.dto.response.AdminUserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// User 도메인의 관리자 조회/제재 기능을 담당하는 서비스
public interface UserModerationService {

    // Admin 유저 목록 조회용
    Page<User> getUsersForAdmin(
            UserStatus status,
            String keyword,
            Pageable pageable
    );

    // email, university를 포함한 관리자용 단건 조회
    AdminUserInfoDto getAdminUserInfo(Long userId);

    // Admin 유저 계정 정지
    // days: 정지 일수 (null = 영구정지)
    void suspendUser(Long userId, Integer days);

    // 신고 기능 박탈 처리 — 기각 누적 초과 시 호출
    void banReportFeature(Long userId, int days);

    // 박탈 여부 체크
    boolean isReportBanned(Long userId);

}
