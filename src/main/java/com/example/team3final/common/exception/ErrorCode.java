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
    OTP_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_006", "OTP 코드가 일치하지 않습니다."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_007", "OTP가 만료되었습니다."),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_008", "OTP 시도 횟수를 초과했습니다. 새 인증번호를 요청하세요."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "존재하지 않는 유저입니다."),
    LOGIN_FAIL(HttpStatus.UNAUTHORIZED, "AUTH_002", "이메일 또는 비밀번호가 일치하지 않습니다."),
    USER_SUSPENDED_OR_WITHDRAWN(HttpStatus.FORBIDDEN, "AUTH_003", "정지 또는 탈퇴된 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "유효하지 않거나 만료된 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "만료된 토큰입니다."),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않는 게시글입니다."),
    POST_INVALID_MEET_AT(HttpStatus.BAD_REQUEST, "POST_002", "만남 희망 시간은 현재 이후여야 합니다."),
    POST_INVALID_DEPOSIT(HttpStatus.BAD_REQUEST, "POST_003", "책임비 포인트는 최소 200P 이상, 100P 단위여야 합니다."),

    // Match
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_001", "존재하지 않는 매칭입니다."),
    MATCH_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "MATCH_002", "해당 매칭의 당사자가 아닙니다."),
    MATCH_SELF_APPLY(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_003", "본인 게시글에는 신청할 수 없습니다."),
    MATCH_ALREADY_MATCHED(HttpStatus.CONFLICT, "MATCH_004", "이미 매칭된 게시글입니다."),
    MATCH_POST_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_005", "모집이 종료된 게시글입니다."),
    MATCH_INVALID_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_006", "현재 상태의 매칭은 취소할 수 없습니다."),
    MATCH_AFTER_MEET_TIME(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_007", "약속 시간 이후에는 취소할 수 없습니다."),

    // MeetVerification
    MEET_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "VERIFY_001", "인증 정보를 찾을 수 없습니다"),
    // MeetVerification - GPS 장소 인증 에러 코드
    GPS_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_002", "약속 장소 반경 50m를 벗어났습니다."),
    GPS_NOT_VERIFICATION_TIME(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_003", "현재는 장소 인증 가능 시간이 아닙니다."),
    GPS_ALREADY_VERIFIED(HttpStatus.CONFLICT, "VERIFY_004", "이미 인증을 완료했습니다."),
    // MeetVerification - QR 토큰 조회 에러 코드
    QR_NOT_AUTHOR(HttpStatus.FORBIDDEN, "VERIFY_005", "등록자만 QR을 발급받을 수 있습니다."),
    QR_PLACE_VERIFICATION_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_006", "장소 인증이 선행되어야 합니다."),
    QR_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_007", "QR 토큰이 만료되었습니다."),
    // MeetVerification - QR 스캔 에러 코드
    SCAN_NOT_APPLICANT(HttpStatus.FORBIDDEN, "VERIFY_008", "신청자만 QR을 스캔할 수 있습니다."),
    SCAN_INVALID_QR_TOKEN(HttpStatus.BAD_REQUEST, "VERIFY_009", "유효하지 않은 QR 토큰입니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "CHAT_002", "이미 존재하는 채팅방입니다."),
    CHAT_ROOM_INACTIVE(HttpStatus.FORBIDDEN, "CHAT_003", "비활성화된 채팅방입니다."),
    CHAT_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "CHAT_004", "해당 채팅방의 참여자가 아닙니다."),
    CHAT_ROOM_RE_ENTER_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_005", "재입장할 수 없는 채팅방입니다."),
    CHAT_ROOM_ACTIVE_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "CHAT_006", "진행 중인 채팅방은 나갈 수 없습니다."),


    // University
    UNIVERSITY_NOT_FOUND(HttpStatus.NOT_FOUND, "UNIVERSITY_001", "조회 가능한 대학 목록이 없습니다."),

    // PointTransaction
    POINT_TRANSACTION_INVALID_TYPE(HttpStatus.BAD_REQUEST, "POINT_001", "유효하지 않은 포인트 거래 타입입니다."),
    POINT_TRANSACTION_INVALID_PAGE(HttpStatus.BAD_REQUEST, "POINT_002", "페이지 요청 값이 올바르지 않습니다."),
    POINT_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_003", "포인트 거래 내역이 없습니다."),
    POINT_NOT_ENOUGH(HttpStatus.UNPROCESSABLE_ENTITY, "POINT_004", "보유 포인트가 부족합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
