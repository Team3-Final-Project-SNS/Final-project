package com.example.team3final.domain.admin.service;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks
    private AdminServiceImpl adminService;

    @Mock
    private AdminRepository adminRepository;

    @Test
    @DisplayName("활성 관리자 ID 목록 조회 - 성공")
    void getActiveAdminIds_Success() {
        // given
        Admin admin = mock(Admin.class);
        given(admin.getId()).willReturn(1L);
        given(adminRepository.findAllByIsActiveTrue()).willReturn(List.of(admin));

        // when
        List<Long> result = adminService.getActiveAdminIds();

        // then
        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("관리자 검증 - 성공")
    void validateAdmin_Success() {
        Admin admin = mock(Admin.class);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));

        adminService.validateAdmin(1L);

        assertThat(admin).isNotNull();
    }
}
