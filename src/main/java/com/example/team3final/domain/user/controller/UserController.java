package com.example.team3final.domain.user.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.example.team3final.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
public class UserController {

    private final UserService userService;

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<GetUserResponseDto>> getUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUserId();
        GetUserResponseDto response = userService.getUser(userId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 내 정보 수정
    @PatchMapping("/me")
    public ResponseEntity<ApiResponseDto<UpdateUserResponseDto>> updateUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateUserRequestDto request
    ) {
        // JWT에서 검증된 userId 추출
        Long userId = userDetails.getUserId();
        UpdateUserResponseDto response = userService.updateUser(userId, request);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
