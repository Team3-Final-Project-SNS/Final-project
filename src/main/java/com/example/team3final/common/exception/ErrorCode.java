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

    // ===== Post 도메인 =====
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않는 게시글입니다."),
    POST_INVALID_MEET_AT(HttpStatus.BAD_REQUEST, "POST_001", "만남 희망 시간은 현재 이후여야 합니다."),
    POST_INVALID_DEPOSIT(HttpStatus.BAD_REQUEST, "POST_002", "책임비 포인트는 최소 200P 이상, 100P 단위여야 합니다."),

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
    SCAN_INVALID_QR_TOKEN(HttpStatus.BAD_REQUEST, "VERIFY_002", "유효하지 않은 QR 토큰입니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "CHAT_002", "이미 존재하는 채팅방입니다."),
    CHAT_ROOM_INACTIVE(HttpStatus.FORBIDDEN, "CHAT_003", "비활성화된 채팅방입니다."),
    CHAT_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "CHAT_004", "해당 채팅방의 참여자가 아닙니다."),
    CHAT_ROOM_RE_ENTER_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_005", "재입장할 수 없는 채팅방입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}