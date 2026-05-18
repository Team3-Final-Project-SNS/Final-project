package com.example.team3final.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth - OTP
    INVALID_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST, "AUTH_001", "학교 이메일(.ac.kr) 형식이 아닙니다."),
    UNREGISTERED_UNIVERSITY(HttpStatus.BAD_REQUEST, "AUTH_002", "등록되지 않은 학교 도메인입니다."),
    ALREADY_REGISTERED_EMAIL(HttpStatus.CONFLICT, "AUTH_003", "이미 가입된 이메일입니다."),
    OTP_SEND_TOO_MANY(HttpStatus.TOO_MANY_REQUESTS, "AUTH_004", "OTP 발송 요청이 너무 많습니다."),
    OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH_005", "재발송은 1분 후에 가능합니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "존재하지 않는 유저입니다."),

    // GPS 장소 인증 에러 코드
    GPS_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_001", "약속 장소 반경 50m를 벗어났습니다."),
    GPS_NOT_VERIFICATION_TIME(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_002", "현재는 장소 인증 가능 시간이 아닙니다."),
    GPS_ALREADY_VERIFIED(HttpStatus.CONFLICT, "VERIFY_003", "이미 인증을 완료했습니다."),

    // QR 토큰 조회 에러 코드
    QR_NOT_AUTHOR(HttpStatus.FORBIDDEN, "VERIFY_001", "등록자만 QR을 발급받을 수 있습니다."),
    QR_PLACE_VERIFICATION_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_002", "장소 인증이 선행되어야 합니다."),
    QR_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_003", "QR 토큰이 만료되었습니다."),

    // QR 스캔 에러 코드
    SCAN_NOT_APPLICANT(HttpStatus.FORBIDDEN, "VERIFY_001", "신청자만 QR을 스캔할 수 있습니다."),
    SCAN_INVALID_QR_TOKEN(HttpStatus.BAD_REQUEST, "VERIFY_002", "유효하지 않은 QR 토큰입니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
