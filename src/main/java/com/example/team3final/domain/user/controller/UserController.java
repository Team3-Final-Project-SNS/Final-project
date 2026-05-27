package com.example.team3final.domain.user.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.request.WithdrawRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.dto.response.WithdrawResponseDto;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.example.team3final.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

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

    // 회원 탈퇴
    // DELETE /api/v1/users/me
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponseDto<WithdrawResponseDto>> withdrawUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails, // JWT에서 꺼낸 인증된 유저
            @Valid @RequestBody WithdrawRequestDto request,        // 비밀번호 확인용 요청 바디
            HttpServletRequest httpRequest,                        // 쿠키에서 refresh_token 추출용
            HttpServletResponse httpResponse                       // 쿠키 파기용
    ) {
        Long userId = userDetails.getUserId();

        // 쿠키에서 refresh_token 추출 (없으면 null → AuthService에서 안전 처리)
        String refreshToken = extractRefreshTokenFromCookie(httpRequest);

        // AuthService에 오케스트레이션 위임
        // (Redis 삭제 → DB 상태 변경 → 쿠키 파기)
        WithdrawResponseDto response = authService.withdraw(userId, request, refreshToken, httpResponse);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 쿠키 배열에서 refresh_token 값을 찾아 반환하는 헬퍼 메서드
    // AuthController의 동일 메서드와 중복이지만, Controller 간 직접 호출은 안티패턴
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
