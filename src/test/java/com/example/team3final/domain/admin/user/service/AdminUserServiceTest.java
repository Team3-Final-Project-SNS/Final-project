package com.example.team3final.domain.admin.user.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.admin.user.dto.request.AdminReinstateUserRequestDto;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminReinstateUserResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private UserService userService;
    @Mock
    private UniversityService universityService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("getUsers returns paged users with university names")
    void getUsers_Success() {
        // given
        User user = createUser(1L, UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 10);

        given(userService.getUsersForAdmin(UserStatus.ACTIVE, "test", pageable))
                .willReturn(new PageImpl<>(List.of(user), pageable, 1));
        given(universityService.getUniversityName(List.of(10L)))
                .willReturn(new HashMap<>(Map.of(10L, "Test University")));

        // when
        PageResponseDto<AdminGetUsersResponseDto> result =
                adminUserService.getUsers(UserStatus.ACTIVE, "test", pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUsers throws when university name is missing")
    void getUsers_UniversityMissing_ThrowsException() {
        // given
        User user = createUser(1L, UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 10);

        given(userService.getUsersForAdmin(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(user), pageable, 1));
        given(universityService.getUniversityName(List.of(10L)))
                .willReturn(new HashMap<>());

        // when & then
        assertThatThrownBy(() -> adminUserService.getUsers(null, null, pageable))
                .isInstanceOf(AdminException.class);
    }

    @Test
    @DisplayName("suspendUser suspends user when admin is super admin")
    void suspendUser_Success() {
        // given
        Long adminId = 1L;
        Long userId = 2L;
        AdminSuspendUserRequestDto request = new AdminSuspendUserRequestDto();
        ReflectionTestUtils.setField(request, "reason", "policy violation");
        User user = createUser(userId, UserStatus.SUSPENDED);

        given(adminRepository.findById(adminId)).willReturn(Optional.of(createSuperAdmin()));
        given(userService.findUserById(userId)).willReturn(user);

        // when
        AdminSuspendUserResponseDto result = adminUserService.suspendUser(adminId, userId, request);

        // then
        assertThat(result.userId()).isEqualTo(userId);
        verify(userService).suspendUser(userId, null);
    }

    @Test
    @DisplayName("suspendUser throws when admin is not super admin")
    void suspendUser_NotSuperAdmin_ThrowsException() {
        // given
        Admin admin = createSuperAdmin();
        admin.deactivate();
        AdminSuspendUserRequestDto request = new AdminSuspendUserRequestDto();

        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));

        // when & then
        assertThatThrownBy(() -> adminUserService.suspendUser(1L, 2L, request))
                .isInstanceOf(AdminException.class);
    }

    @Test
    @DisplayName("reinstateUser reinstates suspended user and sends notification")
    void reinstateUser_Success() {
        // given
        Long adminId = 1L;
        Long userId = 2L;
        AdminReinstateUserRequestDto request = new AdminReinstateUserRequestDto();
        ReflectionTestUtils.setField(request, "reason", "appeal accepted");
        User user = createUser(userId, UserStatus.SUSPENDED);

        given(adminRepository.findById(adminId)).willReturn(Optional.of(createSuperAdmin()));
        given(userService.findUserById(userId)).willReturn(user);

        // when
        AdminReinstateUserResponseDto result = adminUserService.reinstateUser(adminId, userId, request);

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(notificationPublisher).sendAccountUnsuspended(userId);
    }

    @Test
    @DisplayName("reinstateUser throws when user is not suspended")
    void reinstateUser_NotSuspended_ThrowsException() {
        // given
        User user = createUser(2L, UserStatus.ACTIVE);
        AdminReinstateUserRequestDto request = new AdminReinstateUserRequestDto();

        given(adminRepository.findById(1L)).willReturn(Optional.of(createSuperAdmin()));
        given(userService.findUserById(2L)).willReturn(user);

        // when & then
        assertThatThrownBy(() -> adminUserService.reinstateUser(1L, 2L, request))
                .isInstanceOf(UserException.class);
    }

    private Admin createSuperAdmin() {
        return Admin.builder()
                .email("admin@test.com")
                .password("password")
                .name("admin")
                .role(AdminRole.SUPER_ADMIN)
                .build();
    }

    private User createUser(Long id, UserStatus status) {
        User user = User.builder()
                .email("user" + id + "@test.com")
                .password("password")
                .name("user")
                .nickname("nickname" + id)
                .universityId(10L)
                .major("Computer Science")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        if (status == UserStatus.SUSPENDED) {
            user.suspend(null);
        } else if (status == UserStatus.WITHDRAWN) {
            user.withdraw();
        }
        return user;
    }
}
