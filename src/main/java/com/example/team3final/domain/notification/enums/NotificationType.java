package com.example.team3final.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    // ── 매칭 ──────────────────────────────────────────────────────────────
    MATCH_APPLIED("게시글 신청"),                          // 1
    MATCH_CONFIRMED("매칭 확정"),                          // 2
    MATCH_CANCELLED("매칭 취소"),                          // 3, 4

    // ── 만남 시간 ─────────────────────────────────────────────────────────
    MEET_REMINDER("만남 시간 알림"),                       // 5, 6
    MEET_IMMINENT("만남 시간 임박"),                       // 7
    MEET_OVERDUE("만남 시간 경과"),                        // 8

    // ── 만남 완료 / 후기 ──────────────────────────────────────────────────
    MEET_COMPLETED("만남 완료"),                           // 9
    REVIEW_DEADLINE_REMINDER("후기 작성 마감 임박"),       // 10
    REVIEW_REWARD("후기 작성"),                            // 11
    MANNER_TEMPERATURE_CHANGED("매너 온도 변경"),          // 12

    // ── 채팅 / 장소 인증 ──────────────────────────────────────────────────
    CHAT_RECEIVED("채팅 메시지 수신"),                     // 13
    PLACE_VERIFIED("장소 인증 완료"),                      // 14
    CHAT_MEMBER_LEFT("채팅방 퇴장"),                       // 15

    // ── 노쇼 ──────────────────────────────────────────────────────────────
    NO_SHOW_WARNING("노쇼 예정"),                          // 16
    NO_SHOW_CONFIRMED("노쇼 확정"),                        // 17

    // ── 만남 시간 연장 ────────────────────────────────────────────────────
    MEET_EXTEND_REQUESTED("만남 시간 연장 요청"),          // 18
    MEET_EXTEND_ACCEPTED("만남 시간 연장 수락"),           // 19
    MEET_EXTEND_REJECTED("만남 시간 연장 거절"),           // 20
    MEET_EXTEND_EXPIRED("만남 시간 연장 만료"),            // 21

    // ── 이의제기 ──────────────────────────────────────────────────────────
    DISPUTE_SUBMITTED("이의제기 접수"),                    // 22
    DISPUTE_RESULT("이의제기 결과"),                       // 23
    DISPUTE_PENDING("이의제기 보류"),                      // 24
    DISPUTE_DEADLINE_REMINDER("이의제기 증빙 마감 임박"),  // 25

    // ── 신고 ──────────────────────────────────────────────────────────────
    REPORT_SUBMITTED("신고 접수"),                         // 26
    REPORT_REWARD("신고 채택"),                            // 27
    REPORT_REJECTED("신고 기각"),                          // 28

    // ── 결제 ──────────────────────────────────────────────────────────────
    PAYMENT_SUCCESS("결제 성공"),                          // 29
    PAYMENT_FAILED("결제 실패"),                           // 30
    PAYMENT_CANCEL_SUCCESS("결제 취소 및 환불 완료"),      // 31
    PAYMENT_CANCEL_FAILED("결제 취소 및 환불 실패"),       // 32

    // ── 문의 ──────────────────────────────────────────────────────────────
    INQUIRY_SUBMITTED("문의 접수"),                        // 33
    INQUIRY_ANSWERED("문의 답변 완료"),                    // 34

    // ── 계정 ──────────────────────────────────────────────────────────────
    ACCOUNT_SUSPENDED("계정 정지"),                        // 35
    ACCOUNT_UNSUSPENDED("계정 정지 해제"),                 // 36

    // ── 게시글 / 신고로 인한 경고 ────────────────────────────────────────
    POST_WARNED_1("게시글 신고 경고 1회"),                 // 37
    POST_WARNED_2("게시글 신고 경고 2회"),                 // 38
    POST_EXPIRING_SOON("게시글 만료 임박"),                // 39
    POST_EXPIRED("게시글 만료"),                           // 40
    POST_DELETED("게시글 삭제"),                           // 41
    POST_RESTORED("게시글 복구");                          // 42

    private final String description;
}