package com.example.team3final.domain.admin.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 서비스 단위 테스트")
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    @DisplayName("활성 관리자 ID 목록을 조회한다")
    void getActiveAdminIds_shouldReturnActiveAdminIds() {
        Admin firstAdmin = Admin.createAdmin("admin1@test.com", "password", "관리자1", AdminRole.SUPER_ADMIN);
        Admin secondAdmin = Admin.createAdmin("admin2@test.com", "password", "관리자2", AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(firstAdmin, "id", 1L);
        ReflectionTestUtils.setField(secondAdmin, "id", 2L);
        when(adminRepository.findAllByIsActiveTrue()).thenReturn(List.of(firstAdmin, secondAdmin));

        List<Long> result = adminService.getActiveAdminIds();

        assertThat(result).containsExactly(1L, 2L);
        verify(adminRepository).findAllByIsActiveTrue();
    }

    @Test
    @DisplayName("존재하는 관리자 ID 검증은 예외 없이 통과한다")
    void validateAdmin_shouldPassWhenAdminExists() {
        Admin admin = Admin.createAdmin("admin@test.com", "password", "관리자", AdminRole.SUPER_ADMIN);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        adminService.validateAdmin(1L);

        verify(adminRepository).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 관리자 ID 검증은 관리자 예외를 던진다")
    void validateAdmin_shouldThrowWhenAdminNotFound() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.validateAdmin(1L))
                .isInstanceOf(AdminException.class);

        verify(adminRepository).findById(1L);
    }
}
