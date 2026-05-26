package com.example.team3final.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth + OTP
    INVALID_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST, "AUTH_001", "학교 이메일(.ac.kr) 형식이 아닙니다."),
    UNREGISTERED_UNIVERSITY(HttpStatus.BAD_REQUEST, "AUTH_002", "등록되지 않은 학교 도메인입니다."),
    ALREADY_REGISTERED_EMAIL(HttpStatus.CONFLICT, "AUTH_003", "이미 가입된 이메일입니다."),
    OTP_SEND_TOO_MANY(HttpStatus.TOO_MANY_REQUESTS, "AUTH_004", "OTP 발송 요청이 너무 많습니다."),
    OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH_005", "재발송은 1분 후에 가능합니다."),
    OTP_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_006", "OTP 코드가 일치하지 않습니다."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_007", "OTP가 만료되었습니다."),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_008", "OTP 시도 횟수를 초과했습니다. 새 인증번호를 요청하세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "AUTH_009", "이미 사용 중인 닉네임입니다."),
    LOGIN_FAIL(HttpStatus.UNAUTHORIZED, "AUTH_010", "이메일 또는 비밀번호가 일치하지 않습니다."),
    USER_SUSPENDED_OR_WITHDRAWN(HttpStatus.FORBIDDEN, "AUTH_011", "정지 또는 탈퇴된 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_012", "유효하지 않거나 만료된 토큰입니다."),
    // Term
    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERM_001", "필수 약관에 동의하지 않았습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "존재하지 않는 유저입니다."),
    USER_CURRENT_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "USER_002", "현재 비밀번호가 일치하지 않습니다."),
    USER_SAME_PASSWORD(HttpStatus.BAD_REQUEST, "USER_003", "새 비밀번호가 현재 비밀번호와 동일합니다."),
    // common
    USER_NO_FIELD_TO_UPDATE(HttpStatus.BAD_REQUEST, "COMMON_001", "수정할 필드가 한 개 이상 필요합니다."),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않는 게시글입니다."),
    POST_FORBIDDEN_UNIVERSITY(HttpStatus.FORBIDDEN, "POST_002", "다른 학교의 게시글은 조회할 수 없습니다."),
    POST_INVALID_MEET_AT(HttpStatus.BAD_REQUEST, "POST_003", "만남 희망 시간은 현재 이후여야 합니다."),
    POST_INVALID_DEPOSIT(HttpStatus.BAD_REQUEST, "POST_004", "책임비 포인트는 최소 200P 이상, 100P 단위여야 합니다."),
    POST_NOT_AUTHOR(HttpStatus.FORBIDDEN, "POST_005", "본인 게시글만 수정/삭제할 수 있습니다."),
    POST_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "POST_006", "OPEN 상태의 게시글만 수정/삭제할 수 있습니다."),
    POST_INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "POST_007", "페이지 크기는 최대 50까지 요청할 수 있습니다."),
    POST_NOT_MATCHED(HttpStatus.UNPROCESSABLE_ENTITY, "POST_008", "매칭된 게시글만 완료 처리할 수 있습니다."),

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

    // MeetVerification - 만남 시간 연장
    MEET_EXTEND_BEFORE_MEET_AT(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_010", "연장은 약속 시간 5분 전까지만 가능합니다."),
    MEET_EXTEND_ALREADY_ACCEPTED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_011", "이미 연장이 완료된 매칭입니다."),
    MEET_EXTEND_MATCH_NOT_MATCHED(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_012", "MATCHED 상태의 매칭만 연장할 수 있습니다."),
    MEET_EXTEND_ALREADY_REQUESTED(HttpStatus.CONFLICT, "VERIFY_013", "이미 진행 중인 연장 요청이 있습니다."),
    MEET_EXTEND_SELF_RESPONSE(HttpStatus.FORBIDDEN, "VERIFY_014", "본인이 요청한 연장은 본인이 응답할 수 없습니다."),
    MEET_EXTEND_NO_ACTIVE_REQUEST(HttpStatus.UNPROCESSABLE_ENTITY, "VERIFY_015", "응답 가능한 연장 요청이 없습니다."),
    MEET_EXTEND_EXPIRED(HttpStatus.GONE, "VERIFY_016", "연장 요청이 만료되었습니다."),


    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "CHAT_002", "이미 존재하는 채팅방입니다."),
    CHAT_ROOM_INACTIVE(HttpStatus.FORBIDDEN, "CHAT_003", "비활성화된 채팅방입니다."),
    CHAT_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "CHAT_004", "해당 채팅방의 참여자가 아닙니다."),
    CHAT_ROOM_RE_ENTER_FORBIDDEN(HttpStatus.FORBIDDEN, "CHAT_005", "재입장할 수 없는 채팅방입니다."),
    CHAT_ROOM_ACTIVE_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "CHAT_006", "진행 중인 채팅방은 나갈 수 없습니다."),
    CHAT_INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "CHAT_007", "페이지 크기는 최대 50까지 요청할 수 있습니다."),
    CHAT_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "CHAT_008", "유효하지 않은 커서 ID입니다."),


    // University
    UNIVERSITY_NOT_FOUND(HttpStatus.NOT_FOUND, "UNIVERSITY_001", "조회 가능한 대학 목록이 없습니다."),

    // PointTransaction
    POINT_TRANSACTION_INVALID_PAGE(HttpStatus.BAD_REQUEST, "POINT_001", "페이지 요청 값이 올바르지 않습니다."),
    POINT_NOT_ENOUGH(HttpStatus.UNPROCESSABLE_ENTITY, "POINT_002", "보유 포인트가 부족합니다."),

    // Admin
    // 인증
    ADMIN_LOGIN_FAIL(HttpStatus.UNAUTHORIZED, "ADMIN_001", "이메일 또는 비밀번호가 일치하지 않습니다."),
    ADMIN_ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "ADMIN_002", "비활성화된 관리자 계정입니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_003", "존재하지 않는 관리자입니다."),
    // 권한
    ADMIN_SUPER_REQUIRED(HttpStatus.FORBIDDEN, "ADMIN_004", "SUPER_ADMIN 권한이 필요합니다."),
    ADMIN_USER_ALREADY_SUSPENDED(HttpStatus.CONFLICT, "ADMIN_005", "이미 정지된 계정입니다."),
    // Post 강제 삭제
    ADMIN_POST_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "ADMIN_006", "OPEN 상태의 게시글만 삭제할 수 있습니다."),

    // Report
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_001", "존재하지 않는 신고입니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "REPORT_002", "이미 처리된 신고입니다."),
    REPORT_NOT_OWNER(HttpStatus.FORBIDDEN, "REPORT_003", "본인의 신고만 취소할 수 있습니다."),
    REPORT_TOO_SOON(HttpStatus.CONFLICT, "REPORT_004", "3일 이내 동일 게시글 재신고는 불가합니다."),
    REPORT_SELF_REPORT(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_005", "본인의 게시글은 신고할 수 없습니다."),
    REPORT_ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT_006", "이미 신고한 게시글입니다."),
    // Report - admin
    REPORT_NOT_ACCEPTED(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_007", "채택(ACCEPTED)된 신고만 게시글 삭제에 사용할 수 있습니다."),
    REPORT_POST_ID_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_008", "신고 대상 게시글과 요청 게시글이 일치하지 않습니다."),
    REPORT_TARGET_TYPE_NOT_POST(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_009", "게시글(POST) 유형의 신고만 사용할 수 있습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
