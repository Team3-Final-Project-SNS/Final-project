package com.example.team3final.domain.admin.user.service;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private UserService userService;
    @Mock
    private UniversityService universityService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Test
    @DisplayName("유저 계정 정지 - 성공")
    void suspendUser_Success() {
        // given
        Admin admin = mock(Admin.class);
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));

        AdminSuspendUserRequestDto request = new AdminSuspendUserRequestDto();
        ReflectionTestUtils.setField(request, "reason", "Bad behavior");

        // when
        AdminSuspendUserResponseDto result = adminUserService.suspendUser(1L, 100L, request);

        // then
        assertNotNull(result);
        verify(userService).suspendUser(100L, null);
    }

    @Test
    @DisplayName("유저 계정 정지 - 관리자 없음")
    void suspendUser_AdminNotFound() {
        // given
        given(adminRepository.findById(1L)).willReturn(Optional.empty());

        AdminSuspendUserRequestDto request = new AdminSuspendUserRequestDto();
        ReflectionTestUtils.setField(request, "reason", "Bad behavior");

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.AdminException.class, () -> {
            adminUserService.suspendUser(1L, 100L, request);
        });
    }

    @Test
    @DisplayName("유저 계정 정지 - 슈퍼 관리자 아님")
    void suspendUser_NotSuperAdmin() {
        // given
        Admin admin = mock(Admin.class);
        given(admin.isActiveAndSuperAdmin()).willReturn(false);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));

        AdminSuspendUserRequestDto request = new AdminSuspendUserRequestDto();
        ReflectionTestUtils.setField(request, "reason", "Bad behavior");

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.AdminException.class, () -> {
            adminUserService.suspendUser(1L, 100L, request);
        });
    }
}
