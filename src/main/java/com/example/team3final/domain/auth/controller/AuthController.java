package com.example.team3final.domain.auth.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.auth.dto.request.*;
import com.example.team3final.domain.auth.dto.response.*;
import com.example.team3final.domain.auth.service.AuthService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/auth")
public class AuthController {

    private final AuthService authService;

    // 공통 에러 응답 예시 상수

    // 400: 유효성 검증 실패
    private static final String EXAMPLE_400_VALIDATION = """
            {
              "success": false,
              "code": "AUTH_001",
              "message": "학교 이메일(.ac.kr) 형식이 아닙니다.",
              "data": null
            }
            """;

    // 401: 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 409: 이미 가입된 이메일
    private static final String EXAMPLE_409_EMAIL = """
            {
              "success": false,
              "code": "AUTH_003",
              "message": "이미 가입된 이메일입니다.",
              "data": null
            }
            """;

    // 429: 요청 너무 많음
    private static final String EXAMPLE_429 = """
            {
              "success": false,
              "code": "OTP_001",
              "message": "OTP 발송 요청이 너무 많습니다.",
              "data": null
            }
            """;

    // OTP 인증번호 이메일 발송
    @Operation(
            summary = "OTP 인증번호 발송",
            description = """
                    학교 이메일로 6자리 OTP 인증번호를 발송합니다.
                    - 학교 이메일(.ac.kr) 형식만 허용합니다.
                    - 등록된 학교 도메인만 허용합니다.
                    - 1분 이내 재발송 불가 (쿨다운)
                    - 24시간 내 5회 초과 발송 불가
                    - **인증 없이 호출 가능** (로그인 불필요)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP 발송 성공 - 유효시간(초) 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "학교 이메일 형식 오류(AUTH_001) / 미등록 학교 도메인(AUTH_002)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_400_VALIDATION))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 이메일 (AUTH_003)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_409_EMAIL))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "발송 너무 많음(OTP_001) / 1분 쿨다운(OTP_002)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_429))
            )
    })
    @PostMapping("/email/otp")
    public ResponseEntity<ApiResponseDto<OtpResponseDto>> sendEmailOtp(
            @RequestBody @Valid OtpRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(authService.sendEmailOtp(request)));
    }

    // OTP 검증 및 signup_token 발급
    @Operation(
            summary = "OTP 검증 및 signup_token 발급",
            description = """
                    OTP 코드를 검증하고 회원가입용 signup_token을 HttpOnly 쿠키로 발급합니다.
                    - 검증 성공 시 `signup_token` 쿠키가 자동으로 응답에 포함됩니다.
                    - signup_token은 15분간 유효하며 `/api/v1/auth/signup` 경로에만 전송됩니다.
                    - OTP 5회 초과 실패 시 잠금 → 새 OTP 재발송 필요
                    - **인증 없이 호출 가능** (로그인 불필요)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP 검증 성공 - signup_token 쿠키 발급 + 학교 정보 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "OTP 코드 불일치(OTP_003) / OTP 만료(OTP_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "OTP_003",
                                      "message": "OTP 코드가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "OTP 시도 횟수 초과 - 새 OTP 재발송 필요 (OTP_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "OTP_005",
                                      "message": "OTP 시도 횟수를 초과했습니다. 새 인증번호를 요청하세요.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/email/otp/verify")
    public ResponseEntity<ApiResponseDto<OtpVerifyResponseDto>> verifyEmailOtp(
            @RequestBody @Valid OtpVerifyRequestDto request,
            HttpServletResponse response) { // signup_token 쿠키를 응답에 담기 위해 필요
        return ResponseEntity.ok(
                ApiResponseDto.success(authService.verifyEmailOtp(request, response))
        );
    }

    // 회원가입
    @Operation(
            summary = "회원가입",
            description = """
                    OTP 검증 후 발급된 signup_token 쿠키를 사용해 회원가입을 완료합니다.
                    - 요청 시 브라우저가 `signup_token` 쿠키를 자동으로 포함해 전송합니다.
                    - 가입 즉시 10,000P 지급됩니다. (JOIN_BONUS)
                    - 가입 완료 후 Access Token이 응답 body에, Refresh Token이 쿠키에 포함됩니다. (자동 로그인)
                    - 필수 약관에 모두 동의해야 합니다.
                    - **인증 없이 호출 가능** (signup_token 쿠키로 대체)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공 - 10,000P 지급 + Access Token 반환"),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 약관 미동의 (TERM_001)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "TERM_001",
                                      "message": "필수 약관에 동의하지 않았습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "signup_token 없음 또는 만료 (AUTH_006)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이메일 중복(AUTH_003) / 닉네임 중복(AUTH_004)",
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
    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto<SignupResponseDto>> signup(
            @RequestBody @Valid SignupRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ResponseEntity
                .status(HttpStatus.CREATED) // 명세서: 201 Created
                .body(ApiResponseDto.success(
                        authService.signup(request, httpRequest, httpResponse)
                ));
    }

    // 로그인
    @Operation(
            summary = "로그인",
            description = """
                    이메일/비밀번호로 로그인합니다.
                    - Access Token은 응답 body에 포함됩니다.
                    - Refresh Token은 HttpOnly 쿠키(`refresh_token`)로 발급됩니다. (JS 접근 불가)
                    - 탈퇴 계정(WITHDRAWN) 및 정지 계정(SUSPENDED)은 로그인이 제한됩니다.
                    - **인증 없이 호출 가능** (로그인 불필요)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 - Access Token 반환 + refresh_token 쿠키 발급"),
            @ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 불일치 (AUTH_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_005",
                                      "message": "이메일 또는 비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 계정(USER_004) / 정지 계정(USER_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_004",
                                      "message": "탈퇴된 계정입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(
            @RequestBody @Valid LoginRequestDto request,
            HttpServletResponse response) {
        // response를 넘기는 이유: Refresh Token 쿠키를 서비스에서 직접 세팅하기 위해
        return ResponseEntity.ok(ApiResponseDto.success(authService.login(request, response)));
    }

    // 토큰 재발급
    @Operation(
            summary = "Access Token 재발급",
            description = """
                    HttpOnly 쿠키의 Refresh Token으로 새 Access Token을 재발급합니다.
                    - 브라우저가 `refresh_token` 쿠키를 자동으로 포함해 전송합니다.
                    - Access Token 만료 시 이 API를 호출해 재발급받습니다.
                    - Refresh Token도 함께 갱신됩니다. (Refresh Token Rotation)
                    - **인증 없이 호출 가능** (refresh_token 쿠키로 대체)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공 - 새 Access Token 반환 + refresh_token 쿠키 갱신"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token 없음 또는 만료 (AUTH_006)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto<TokenResponseDto>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        // 쿠키에서 refresh_token 꺼내기
        String refreshToken = extractRefreshTokenFromCookie(request);
        return ResponseEntity.ok(ApiResponseDto.success(authService.refresh(refreshToken, response)));
    }

    // 로그아웃
    @Operation(
            summary = "로그아웃",
            description = """
                    로그아웃 처리합니다.
                    - Redis에서 Refresh Token을 삭제합니다. (재사용 방지)
                    - `refresh_token` 쿠키를 만료시킵니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token 없음 또는 만료 (AUTH_006)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // 쿠키에서 refresh_token 꺼내기
        String refreshToken = extractRefreshTokenFromCookie(request);
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }

    // 쿠키 배열에서 refresh_token 값을 찾아 반환하는 헬퍼 메서드
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
