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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    // 공통 에러 응답 예시 상수

    // 401: 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 404: 유저 없음
    private static final String EXAMPLE_404_USER = """
            {
              "success": false,
              "code": "USER_001",
              "message": "존재하지 않는 유저입니다.",
              "data": null
            }
            """;


    // 내 정보 조회
    @Operation(
            summary = "내 정보 조회",
            description = """
                    로그인한 사용자의 프로필 정보를 조회합니다.
                    - 닉네임, 포인트 잔액, 매너온도, 학교 정보 등을 반환합니다.
                    - 정지 계정(SUSPENDED)도 이 API는 호출 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "유저를 찾을 수 없음 (USER_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_USER))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<GetUserResponseDto>> getUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUserId();
        GetUserResponseDto response = userService.getUser(userId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 내 정보 수정
    @Operation(
            summary = "내 정보 수정",
            description = """
                    닉네임 또는 비밀번호를 수정합니다.
                    - 닉네임, 비밀번호 중 최소 한 개 이상 포함해야 합니다.
                    - 비밀번호 변경 시 현재 비밀번호 확인이 필요합니다.
                    - 새 비밀번호는 현재 비밀번호와 달라야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "수정할 필드 없음(USER_007) / 현재 비밀번호 불일치(USER_002) / 새 비밀번호 동일(USER_003)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_007",
                                      "message": "수정할 필드가 한 개 이상 필요합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 닉네임 (AUTH_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_004",
                                      "message": "이미 사용 중인 닉네임입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
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
    @Operation(
            summary = "회원 탈퇴",
            description = """
                    회원 탈퇴를 처리합니다.
                    - 비밀번호 확인 후 탈퇴가 진행됩니다.
                    - 탈퇴 처리 순서: Redis Refresh Token 삭제 → DB 상태 WITHDRAWN 변경 → refresh_token 쿠키 파기
                    - 탈퇴 후 동일 이메일로 재가입이 불가합니다.
                    - 예치 중인 포인트가 있으면 탈퇴가 불가합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공 - refresh_token 쿠키 파기됨"),
            @ApiResponse(
                    responseCode = "401",
                    description = "현재 비밀번호 불일치 (USER_002)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_002",
                                      "message": "현재 비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "유저를 찾을 수 없음 (USER_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_USER))
            )
    })
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
