package com.example.team3final.domain.notification.service;

/**
 * [설계 결정] 메서드를 알림 상황별로 분리한 이유:
 *   - 호출하는 쪽에서 어떤 알림인지 명확하게 알 수 있음
 *   - Enum 방식(send(userId, type, relatedId))은 타입 실수 가능성이 있고
 *     인터페이스 공유 시 Enum도 함께 공유해야 하는 번거로움이 있음
 *   - 새 알림 추가 시 메서드 하나만 추가하면 되므로 확장도 용이함
 * 사용 도메인:
 *   - 매칭: 매칭 신청, 매칭 확정, 매칭 취소(HOST/GUEST)
 *   - 만남 시간: 30분/15분/5분 전, 10분 경과
 *   - 만남 완료 / 후기: 만남 완료, 마감 임박, 포인트 지급, 매너 온도
 *   - 채팅 / 장소 인증: 채팅 수신, 장소 인증, 채팅방 퇴장
 *   - 노쇼: 노쇼 예정, 노쇼 확정
 *   - 만남 시간 연장: 요청, 수락, 거절, 만료
 *   - 이의제기: 접수, 판정 결과, 보류, 마감 임박
 *   - 신고: 접수, 채택, 기각
 *   - 결제: 성공, 실패, 환불 완료, 환불 실패
 *   - 문의: 접수, 답변 완료
 *   - 계정: 정지, 정지 해제
 *   - 게시글 / 경고: 경고 1/2회, 만료 임박, 만료, 삭제
 */
public interface NotificationPublisher {

    // ── 매칭 ──────────────────────────────────────────────────────────────
    // 1. 게시글 신청 알림 - HOST에게
    void sendMatchApplied(Long userId, Long matchId);

    // 2. 매칭 확정 알림 - 등록자에게
    void sendMatchConfirmed(Long userId, Long matchId);

    // 3. GUEST가 신청을 취소했을 때 - HOST에게
    void sendGuestCancelled(Long userId, Long matchId);

    // 4. HOST가 매칭을 취소했을 때 - GUEST에게
    void sendHostCancelled(Long userId, Long matchId);

    // ── 만남 시간 ─────────────────────────────────────────────────────────
    // 5. 만남 30분 전 알림 - 만남 참여자 모두에게
    void sendMeetReminder30(Long userId, Long matchId);

    // 6. 만남 15분 전 알림 - 만남 참여자 모두에게
    void sendMeetReminder15(Long userId, Long matchId);

    // 7. 만남 5분 전 임박 알림 - 만남 참여자 모두에게
    void sendMeetImminent(Long userId, Long matchId);

    // 8. 만남 시간 10분 경과 알림 - 만남 참여자 모두에게
    void sendMeetOverdue(Long userId, Long matchId);

    // ── 만남 완료 / 후기 ──────────────────────────────────────────────────
    // 9. 만남 완료 / 후기 작성 유도 알림 - 신청자에게
    void sendMeetCompleted(Long userId, Long matchId);

    // 10. 후기 작성 마지막 날 알림 - 미작성 신청자에게
    void sendReviewDeadlineReminder(Long userId, Long matchId);

    // 11. 후기 작성 포인트 지급 알림 - 후기 작성자에게
    void sendReviewPoint(Long userId, Long reviewId);

    // 12. 매너 온도 상승 알림 - 후기로 인한 매너온도 반영자에게
    void sendMannerTemperatureChanged(Long userId);

    // ── 채팅 / 장소 인증 ──────────────────────────────────────────────────
    // 13. 채팅 메시지 수신 알림 - 메시지 수신자에게
    void sendChatReceived(Long userId, Long matchId);

    // 14. 장소 인증 완료 알림 - 1:1: 상대방에게 / 그룹: 모임 참여자 전원에게
    void sendPlaceVerified(Long userId, Long matchId);

    // 15. 그룹 채팅방 신청자 퇴장 알림 - 등록자에게만
    void sendChatMemberLeft(Long userId, Long matchId);

    // ── 노쇼 ──────────────────────────────────────────────────────────────
    // 16. 노쇼 예정 알림 - 노쇼 예정 유저에게
    void sendNoShowWarning(Long userId, Long matchId);

    // 17. 노쇼 확정 알림 - 관련 사용자 양측에게
    void sendNoShowConfirmed(Long userId, Long matchId);

    // ── 만남 시간 연장 ────────────────────────────────────────────────────
    // 18. 만남 시간 연장 요청 알림 - 만남 상대방에게
    void sendMeetExtendRequested(Long userId, Long matchId);

    // 19. 만남 시간 연장 수락 알림 - 연장 요청자에게
    void sendMeetExtendAccepted(Long userId, Long matchId);

    // 20. 만남 시간 연장 거절 알림 - 연장 요청자에게
    void sendMeetExtendRejected(Long userId, Long matchId);

    // 21. 만남 시간 연장 만료 알림 - 연장 요청자에게
    void sendMeetExtendExpired(Long userId, Long matchId);

    // ── 이의제기 ──────────────────────────────────────────────────────────
    // 22. 이의제기 접수 알림 - 관리자에게
    void sendDisputeSubmitted(Long adminId, Long disputeId);

    // 23. 이의제기 판정 결과 알림 - 이의제기 신청자에게
    void sendDisputeResult(Long userId, Long disputeId);

    // 24. 이의제기 보류 알림 - 이의제기 신청자에게
    void sendDisputePending(Long userId, Long disputeId);

    // 25. 이의제기 추가 증빙 마감 임박 알림 - 이의제기 신청자에게
    void sendDisputeDeadlineReminder(Long userId, Long disputeId);

    // ── 신고 ──────────────────────────────────────────────────────────────
    // 26. 신고 접수 알림 - 관리자에게
    void sendReportSubmitted(Long adminId, Long reportId);

    // 27. 신고 채택 포인트 지급 알림 - 신고자에게
    void sendReportAcceptedPoint(Long userId, Long reportId);

    // 28. 신고는 채택됐지만 월 보상 한도로 포인트가 지급되지 않은 경우
    void sendReportAcceptedWithoutPoint(Long userId, Long reportId);

    // 29. 신고 기각 알림 - 신고자에게
    void sendReportRejected(Long userId, Long reportId);

    // ── 결제 ──────────────────────────────────────────────────────────────
    // 30. 결제 성공 알림 - 결제 사용자에게
    void sendPaymentSuccess(Long userId, Long paymentId);

    // 31. 결제 실패 알림 - 결제 사용자에게
    void sendPaymentFailed(Long userId, Long paymentId);

    // 32. 결제 취소 및 환불 완료 알림 - 결제 사용자에게
    void sendPaymentCancelSuccess(Long userId, Long paymentId);

    // 33. 결제 취소 및 환불 실패 알림 - 결제 사용자에게
    void sendPaymentCancelFailed(Long userId, Long paymentId);

    // ── 문의 ──────────────────────────────────────────────────────────────
    // 34. 문의 접수 알림 - 관리자에게
    void sendInquirySubmitted(Long adminId, Long inquiryId);

    // 35. 문의 답변 완료 알림 - 문의 작성자에게
    void sendInquiryAnswered(Long userId, Long inquiryId);

    // ── 계정 ──────────────────────────────────────────────────────────────
    // 36. 계정 정지 알림 - 해당 사용자에게
    // 제재 단계별 메시지는 호출하는 쪽(Service)에서 title/content를 분기하여 전달
    void sendAccountSuspended(Long userId, String title, String content);

    // 37. 계정 정지 해제 알림 - 해당 사용자에게
    void sendAccountUnsuspended(Long userId);

    // ── 게시글 / 신고로 인한 경고 ────────────────────────────────────────
    // 38. 게시글 신고 경고 1회 알림 - 게시글 작성자에게
    void sendPostWarned(Long userId, String title, String content);

    // 39. 게시글 만료 알림 - 게시글 작성자에게
    void sendPostExpired(Long userId, Long postId);

    // 40. 게시글 삭제 알림 - 게시글 작성자에게
    void sendPostDeleted(Long userId, Long postId);

    // 41. 게시글 복구 알림 - 게시글 작성자에게
    void sendPostRestored(Long userId, Long postId);
}
