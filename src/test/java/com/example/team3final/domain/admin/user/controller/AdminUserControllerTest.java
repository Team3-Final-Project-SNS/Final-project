package com.example.team3final.domain.admin.user.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.user.dto.request.AdminReinstateUserRequestDto;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.service.AdminUserService;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 사용자 컨트롤러 통합 테스트")
class AdminUserControllerTest extends ControllerTestSupport {

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminUserController(adminUserService));
    }

    @Test
    @DisplayName("관리자 사용자 목록 조회 API는 상태와 검색어를 바인딩한다")
    void getUsers_shouldBindFiltersAndPageable() throws Exception {
        when(adminUserService.getUsers(eq(UserStatus.ACTIVE), eq("kim"), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("status", "ACTIVE")
                        .param("keyword", "kim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminUserService).getUsers(eq(UserStatus.ACTIVE), eq("kim"), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 사용자 정지 API는 관리자 ID, 사용자 ID, 정지 요청을 서비스로 전달한다")
    void suspendUser_shouldPassAdminIdUserIdAndRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/10/suspend")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminUserService).suspendUser(eq(1L), eq(10L), any(AdminSuspendUserRequestDto.class));
    }

    @Test
    @DisplayName("관리자 사용자 정지 해제 API는 관리자 ID, 사용자 ID, 해제 요청을 서비스로 전달한다")
    void reinstateUser_shouldPassAdminIdUserIdAndRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/10/reinstate")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"appeal accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminUserService).reinstateUser(eq(1L), eq(10L), any(AdminReinstateUserRequestDto.class));
    }
}
