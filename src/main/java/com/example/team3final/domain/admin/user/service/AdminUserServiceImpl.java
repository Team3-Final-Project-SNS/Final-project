package com.example.team3final.domain.admin.user.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.admin.user.dto.request.AdminReinstateUserRequestDto;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminReinstateUserResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityInternalService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminRepository adminRepository;
    private final UserModerationService userModerationService;
    private final UserInternalService userInternalService;
    private final UniversityInternalService universityInternalService;
    private final NotificationPublisher notificationPublisher;

    // 유저 목록 조회
    @Override
    public PageResponseDto<AdminGetUsersResponseDto> getUsers(UserStatus status, String keyword, Pageable pageable) {

        // User 목록 페이징 조회
        Page<User> userPage = userModerationService.getUsersForAdmin(status, keyword, pageable);

        // N+1 방지
        List<Long> universityIds = userPage.getContent().stream()
                .map(User::getUniversityId)
                .distinct()
                .toList();

        // UniversityService 통해서 id -> name 매핑 Map 조회
        Map<Long, String> universityNameMap = universityInternalService.getUniversityName(universityIds);

        // DTO 반환
        Page<AdminGetUsersResponseDto> result = userPage.map(user -> AdminGetUsersResponseDto.of(
                user,
                universityNameMap.computeIfAbsent(user.getUniversityId(), id -> {
                    throw new AdminException(ErrorCode.UNIVERSITY_NOT_FOUND);
                }),
                user.getMannerTemperature().doubleValue()
                )
        );

        return PageResponseDto.from(result);
    }

    // User 계정 정지
    @Override
    @Transactional
    public AdminSuspendUserResponseDto suspendUser(Long adminId, Long userId, AdminSuspendUserRequestDto requestDto) {

        // 1차 방어 -> Admin 계정이 활성화 상태인지 체크
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 2차 방어 -> SUPER_ADMIN인지 확인
        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // 계정 정지 처리
        userModerationService.suspendUser(userId, null); // 영구정지 (관리자 수동 정지)

        // 정지된 유저 엔티티 조회 → 팩토리 메서드로 응답 생성
        User user = userInternalService.findUserById(userId);

        notificationPublisher.sendAccountSuspended(
                userId,
                "계정이 정지되었습니다.",
                "계정이 정지되었습니다. 사유: " + requestDto.getReason()
        );

        return AdminSuspendUserResponseDto.of(user, requestDto.getReason());
    }

    // 관리자 수동 정지 해제
    @Override
    @Transactional
    public AdminReinstateUserResponseDto reinstateUser(Long adminId, Long userId, AdminReinstateUserRequestDto requestDto) {

        // 1차 방어 → Admin 계정 활성화 상태 체크
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 2차 방어 → SUPER_ADMIN인지 확인
        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // 유저 조회
        User user = userInternalService.findUserById(userId);

        // SUSPENDED 상태가 아니면 정지 해제 불가
        // ACTIVE 계정을 실수로 해제하거나, WITHDRAWN 계정을 복구하는 것 방지
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new UserException(ErrorCode.USER_NOT_SUSPENDED);
        }

        // 정지 해제 처리 — ACTIVE 복구 + suspendedUntil 초기화
        // UserService를 거치지 않고 User 엔티티 직접 호출
        // 이유: reinstate()는 단순 상태 변경으로, UserService 인터페이스를 오염시킬 필요 없음
        // @Transactional + 더티 체킹 → save() 없이 자동 UPDATE
        user.reinstate();

        // 36. 계정 정지 해제 알림 - 해당 사용자에게
        notificationPublisher.sendAccountUnsuspended(userId, requestDto.getReason());

        return AdminReinstateUserResponseDto.of(user, requestDto.getReason());
    }
}
