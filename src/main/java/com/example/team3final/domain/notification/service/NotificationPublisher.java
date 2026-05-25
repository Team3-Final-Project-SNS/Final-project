package com.example.team3final.domain.notification.service;
/**
 * [설계 결정] 메서드를 알림 상황별로 분리한 이유:
 *   - 호출하는 쪽에서 어떤 알림인지 명확하게 알 수 있음
 *   - Enum 방식(send(userId, type, relatedId))은 타입 실수 가능성이 있고
 *     인터페이스 공유 시 Enum도 함께 공유해야 하는 번거로움이 있음
 *   - 새 알림 추가 시 메서드 하나만 추가하면 되므로 확장도 용이함
 *
 * 사용 도메인:
 *   - 매칭(정): 매칭 신청, 매칭 취소, 노쇼
 *   - 관리자(정): 이의제기 접수 알림
 *   - 후기(최): 후기 작성 50P 지급
 *   - 결제/이의제기(류): 이의제기 결과
 *   - 신고(박): 신고 처리 결과, 신고 채택 포인트 지급
 *   - 채팅(박): 채팅 메시지 수신 알림
 *   - 고객문의(문): 문의 답변 완료 알림
 */

// 알림 발송 인터페이스
public interface NotificationPublisher {

    // 1. 게시글에 누군가 신청했을 때 - 게시글 작성자에게
    void sendMatchApplied(Long userId, Long matchId);

    // 2. 게시글 신청을 취소했을 때 - 게시글 작성자에게
    void sendMatchCancelled(Long userId, Long matchId);

    // 3. 채팅 메세지 수신 알림 - 메세지 수신자에게
    void sendChatReceived(Long userId, Long chatRoomId);

    // 4. 상대가 장소 인증을 완료했을 때 - 만남 상대방에게
    void sendPlaceVerified(Long userId, Long matchId);

    // 5. 만남 시간 임박 알림 - 만남 참여자에게
    void sendMeetImminent(Long userId, Long matchId);

    // 6. 만남 30분 전 알림 - 만남 참여자에게
    void sendMeetReminder30(Long userId, Long matchId);

    // 7. 만남 15분 전 알림 - 만남 참여자에게
    void sendMeetReminder15(Long userId, Long matchId);

    // 8. 노쇼 확정 알림 - 관련 사용자에게
    void sendNoShowConfirmed(Long userId, Long matchId);

    // 9. 노쇼 예정 알림 - 관련 사용자에게
    void sendNoShowWarning(Long userId, Long matchId);

    // 10. 신고 처리 결과 알림 - 신고자/신고 대상자에게
    void sendReportResult(Long userId, Long reportId);

    // 11. 이의제기 접수 알림 - 관리자에게
    void sendDisputeSubmitted(Long adminId, Long disputeId);

    // 12. 시스템 공지 알림 - 전체/대상 사용자에게
    void sendSystem(Long userId, String title, String content);

    // 13. 신고 채택 포인트 지급 알림 - 신고자에게
    void sendReportAcceptedPoint(Long userId, Long reportId);

    // 14. 후기 작성 포인트 지급 알림 - 후기 작성자에게
    void sendReviewPoint(Long userId, Long reviewId);

    // 15. 문의 답변 완료 알림 - 문의 작성자에게
    void sendInquiryAnswered(Long userId, Long inquiryId);
}
