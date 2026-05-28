package com.example.team3final.domain.admin.user.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminRepository adminRepository;
    private final UserService userService;
    private final UniversityService universityService;

    // 유저 목록 조회
    @Override
    public PageResponseDto<AdminGetUsersResponseDto> getUsers(UserStatus status, String keyword, Pageable pageable) {

        // User 목록 페이징 조회
        Page<User> userPage = userService.getUsersForAdmin(status, keyword, pageable);

        // N+1 방지
        List<Long> universityIds = userPage.getContent().stream()
                .map(User::getUniversityId)
                .distinct()
                .toList();

        // UniversityService 통해서 id -> name 매핑 Map 조회
        Map<Long, String> universityNameMap = universityService.getUniversityName(universityIds);

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
    public AdminSuspendUserResponseDto suspendUser(Long adminId, Long userId, AdminSuspendUserRequestDto requestDto) {

        // 1차 방어 -> Admin 계정이 활성화 상태인지 체크
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 2차 방어 -> SUPER_ADMIN인지 확인
        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // 계정 정지 처리
        userService.suspendUser(userId, null); // 영구정지 (관리자 수동 정지)

        return new AdminSuspendUserResponseDto(
                userId,
                UserStatus.SUSPENDED,
                requestDto.getReason(),
                LocalDateTime.now()
        );
    }
}
