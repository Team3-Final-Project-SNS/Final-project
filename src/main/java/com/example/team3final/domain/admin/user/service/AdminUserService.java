package com.example.team3final.domain.admin.user.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    // 유저 목록 조회
    PageResponseDto<AdminGetUsersResponseDto> getUsers(UserStatus status, String keyword, Pageable pageable);

    // 유저 계정 정지
    AdminSuspendUserResponseDto suspendUser(Long adminId, Long userId, AdminSuspendUserRequestDto requestDto);
}
