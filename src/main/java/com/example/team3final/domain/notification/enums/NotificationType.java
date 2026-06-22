package com.example.team3final.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    // DB에는 enum 이름이 문자열로 저장되므로, 저장된 알림 type은 enum 상수와 반드시 일치해야 한다.
    // 목록 조회 시 Notification 엔티티를 읽는 순간 enum 변환이 수행되므로 누락된 type이 있으면 500이 발생한다.

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
    MEET_COMPLETED_AUTHOR("등록자 만남 완료"),              // 등록자 상세 이동용
    REVIEW_DEADLINE_REMINDER("후기 작성 마감 임박"),       // 10
    REVIEW_REWARD("후기 작성 보상"),                       // 11
    MANNER_TEMPERATURE_CHANGED("매너 온도 변경"),          // 12

    // ── 채팅 / 장소 인증 ──────────────────────────────────────────────────
    CHAT_RECEIVED("채팅 메시지 수신"),                     // 13
    PLACE_VERIFIED("장소 인증 완료"),                      // 14
    CHAT_MEMBER_LEFT("채팅방 이탈"),                       // 15

    // ── 노쇼 ──────────────────────────────────────────────────────────────
    NO_SHOW_WARNING("노쇼 예정"),                          // 16
    OPPONENT_NO_SHOW_WARNING("상대방 노쇼 예정"),
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
    REPORT_REWARD("신고 보상"),                            // 27
    REPORT_REJECTED("신고 기각"),                          // 28
    // 기존 알림 데이터에 신고 처리 결과 type이 남아 있어도 목록 조회가 실패하지 않도록 유지
    REPORT_RESULT("신고 처리 결과"),

    // ── 결제 ──────────────────────────────────────────────────────────────
    PAYMENT_SUCCESS("결제 성공"),                          // 29
    PAYMENT_FAILED("결제 실패"),                           // 30
    PAYMENT_CANCEL_SUCCESS("결제 취소 및 환불 완료"),      // 31
    PAYMENT_CANCEL_FAILED("결제 취소 및 환불 실패"),       // 32
    // 포인트 변경성 알림을 조회할 때 enum 변환 실패가 나지 않도록 유지
    POINT_CHANGED("포인트 변경"),

    // ── 문의 ──────────────────────────────────────────────────────────────
    INQUIRY_SUBMITTED("문의 접수"),                        // 33
    INQUIRY_ANSWERED("문의 답변 완료"),                    // 34

    // ── 계정 ──────────────────────────────────────────────────────────────
    ACCOUNT_SUSPENDED("계정 정지"),                        // 35
    ACCOUNT_UNSUSPENDED("계정 정지 해제"),                 // 36

    // ── 게시글 / 신고로 인한 경고 ────────────────────────────────────────
    POST_WARNED_1("게시글 신고 경고 1차"),                 // 37
    POST_WARNED_2("게시글 신고 경고 2차"),                 // 38
    POST_EXPIRING_SOON("게시글 만료 임박"),                // 39
    POST_EXPIRED("게시글 만료"),                           // 40
    POST_DELETED("게시글 삭제"),                           // 41
    POST_RESTORED("게시글 복구"),                          // 42

    // 시스템성 알림을 조회할 때 enum 변환 실패가 나지 않도록 유지
    SYSTEM("시스템");

    private final String description;
}
