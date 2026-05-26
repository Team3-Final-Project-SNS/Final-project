package com.example.team3final.domain.admin.user.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.admin.user.dto.request.AdminSuspendUserRequestDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminSuspendUserResponseDto;
import com.example.team3final.domain.admin.user.service.AdminUserService;
import com.example.team3final.domain.user.enums.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // 유저 목록 조회
    @GetMapping("/users")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminGetUsersResponseDto>>> getUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponseDto.success(adminUserService.getUsers(status, keyword, pageable)));
    }

    // 유저 계정 정지
    @PatchMapping("/users/{userId}/suspend")
    public ResponseEntity<ApiResponseDto<AdminSuspendUserResponseDto>> suspendUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @Valid @RequestBody AdminSuspendUserRequestDto requestDto) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminUserService.suspendUser(adminId, userId, requestDto)));
    }
}
