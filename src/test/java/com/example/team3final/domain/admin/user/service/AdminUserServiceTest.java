package com.example.team3final.domain.admin.user.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityInternalService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserModerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 사용자 서비스 단위 테스트")
class AdminUserServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private UserModerationService userModerationService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private UniversityInternalService universityInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @Test
    @DisplayName("사용자 목록을 조회하면 운영자 사용자 조회 결과를 페이지 응답으로 반환한다")
    void getUsers_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        User user = user();
        when(userModerationService.getUsersForAdmin(UserStatus.ACTIVE, "검색어", pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(universityInternalService.getUniversityName(List.of(1L)))
                .thenReturn(new HashMap<>(Map.of(1L, "테스트대학교")));

        PageResponseDto<?> response = adminUserService.getUsers(UserStatus.ACTIVE, "검색어", pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        verify(userModerationService).getUsersForAdmin(UserStatus.ACTIVE, "검색어", pageable);
    }

    @Test
    @DisplayName("관리자가 없으면 사용자 정지에 실패한다")
    void suspendUser_shouldThrowWhenAdminNotFound() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.suspendUser(1L, 2L, null))
                .isInstanceOf(AdminException.class);
    }

    @Test
    @DisplayName("슈퍼 관리자가 사용자 정지를 요청하면 사용자 운영 서비스에 정지를 위임한다")
    void suspendUser_shouldDelegateToModerationService() {
        AdminSuspendUserRequestDto requestDto = new AdminSuspendUserRequestDto();
        ReflectionTestUtils.setField(requestDto, "reason", "운영 정책 위반");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(userInternalService.findUserById(2L)).thenReturn(user());

        adminUserService.suspendUser(1L, 2L, requestDto);

        verify(userModerationService).suspendUser(2L, null);
        verify(notificationPublisher).sendAccountSuspended(eq(2L), anyString(), anyString());
    }

    private Admin admin() {
        return Admin.createAdmin("admin@test.com", "encoded", "관리자", AdminRole.SUPER_ADMIN);
    }

    private User user() {
        User user = User.builder()
                .email("user@test.com")
                .password("encoded")
                .name("사용자")
                .nickname("닉네임")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", 2L);
        return user;
    }
}
