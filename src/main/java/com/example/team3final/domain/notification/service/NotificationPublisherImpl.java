package com.example.team3final.domain.notification.service;

import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.notification.dto.event.NotificationEvent;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

// 알림 발송 구현체 (NotificationPublisher 인터페이스)
// 각 sendXxx() 메서드는 DB에 직접 저장하지 않고 Kafka 알림 이벤트를 발행한다.
// 실제 DB 저장 + SSE 전송은 NotificationEventConsumer가 처리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisherImpl implements NotificationPublisher {

    // Kafka에 String 메시지를 발행하는 도구
    private final KafkaTemplate<String, String> kafkaTemplate;

    // DTO -> JSON 문자열 변환 도구
    private final ObjectMapper objectMapper;

    // ==================== 공통 메서드 ====================

    // 알림 이벤트 발행 공통 메서드
    // private: sendXxx() 메서드에서만 사용하는 내부 헬퍼
    private void publish(Long receiverId, NotificationType type, String title,
                         String content, RelatedDomain relatedDomain, Long relatedId) {
        try {

            // 멱등성을 위한 이벤트 고유 ID 생성
            // Consumer가 이 ID를 Redis에 기록해 중복 처리 방지
            String eventId = UUID.randomUUID().toString();

            NotificationEvent event = new NotificationEvent(
                    eventId,
                    receiverId,
                    type,
                    title,
                    content,
                    relatedDomain,
                    relatedId
            );

            // Kafka key는 receiverId로 설정
            // 같은 수신자의 알림은 같은 파티션에 들어가 순서가 유지될 가능성이 높아짐
            String key = String.valueOf(receiverId);
            String message = objectMapper.writeValueAsString(event);

            // Kafka 전송 결과를 비동기로 확인
            // 전송 성공 시 → 정상 처리
            // 전송 실패 시 → DLQ 토픽으로 발행
            kafkaTemplate.send(KafkaTopics.NOTIFICATIONS, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            // 전송 성공
                            log.info("[Kafka Notification Producer] 알림 이벤트 발행 성공" +
                                            " - receiverId: {}, type: {}, relatedId: {}",
                                    receiverId, type, relatedId);
                        } else {
                            // 전송 실패 → DLQ로 발행
                            log.error("[Kafka Notification Producer] 알림 이벤트 발행 실패 → DLQ 발행" +
                                            " - receiverId: {}, type: {}, error: {}",
                                    receiverId, type, ex.getMessage());
                            // DLQ에 원본 메시지 그대로 발행
                            kafkaTemplate.send(KafkaTopics.NOTIFICATIONS_DLQ, key, message);
                        }
                    });

        } catch (Exception e) {
            log.error("[Kafka Notification Producer] 알림 이벤트 직렬화 실패 - receiverId: {}, type: {}, error: {}",
                    receiverId, type, e.getMessage());
        }
    }

    // ==================== NotificationPublisher 구현 ====================

    // ── 매칭 ──────────────────────────────────────────────────────────────

    // 1. 게시글 신청 알림 - HOST에게
    @Override
    public void sendMatchApplied(Long userId, Long matchId) {
        publish(userId, NotificationType.MATCH_APPLIED,
                "새로운 신청자가 있습니다.",
                "게시글에 새로운 신청자가 있습니다. 신청 내용을 확인해 주세요.",
                RelatedDomain.MATCH, matchId);
    }

    // 2. 매칭 확정 알림 - 등록자에게
    @Override
    public void sendMatchConfirmed(Long userId, Long matchId) {
        publish(userId, NotificationType.MATCH_CONFIRMED,
                "매칭이 확정되었습니다.",
                "매칭이 확정되었습니다. 채팅방을 확인해 주세요.",
                RelatedDomain.MATCH, matchId);
    }

    // 3. GUEST가 신청을 취소했을 때 - HOST에게
    @Override
    public void sendGuestCancelled(Long userId, Long matchId) {
        publish(userId, NotificationType.MATCH_CANCELLED,
                "신청이 취소되었습니다.",
                "신청자가 참여 신청을 취소했습니다.",
                RelatedDomain.MATCH, matchId);
    }

    // 4. HOST가 매칭을 취소했을 때 - GUEST에게
    @Override
    public void sendHostCancelled(Long userId, Long matchId) {
        publish(userId, NotificationType.MATCH_CANCELLED,
                "매칭이 취소되었습니다.",
                "게시글 등록자가 매칭을 취소했습니다. 예치 포인트가 환불됩니다.",
                RelatedDomain.MATCH, matchId);
    }

    // ── 만남 시간 ─────────────────────────────────────────────────────────

    // 5. 만남 30분 전 알림 - 만남 참여자에게
    @Override
    public void sendMeetReminder30(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_REMINDER,
                "만남 시간이 30분 남았습니다.",
                "만남 시간이 30분 남았습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 6. 만남 15분 전 알림 - 만남 참여자에게
    @Override
    public void sendMeetReminder15(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_REMINDER,
                "만남 시간이 15분 남았습니다.",
                "만남 시간이 15분 남았습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 7. 만남 5분 전 임박 알림 - 만남 참여자에게
    @Override
    public void sendMeetImminent(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_IMMINENT,
                "만남 시간이 곧 다가옵니다.",
                "만남 시간이 곧 다가옵니다. 준비해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 8. 만남 시간 10분 경과 알림 - 만남 참여자에게
    @Override
    public void sendMeetOverdue(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_OVERDUE,
                "만남 시간이 10분 지났습니다.",
                "만남 시간이 10분 지났습니다.",
                RelatedDomain.MEET, matchId);
    }

    // ── 만남 완료 / 후기 ──────────────────────────────────────────────────

    // 9. 만남 완료 / 후기 작성 유도 알림 - 신청자에게
    @Override
    public void sendMeetCompleted(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_COMPLETED,
                "만남이 완료되었습니다.",
                "만남이 완료되었습니다. 후기를 작성해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 10. 후기 작성 마지막 날 알림 - 미작성 신청자에게
    @Override
    public void sendReviewDeadlineReminder(Long userId, Long matchId) {
        publish(userId, NotificationType.REVIEW_DEADLINE_REMINDER,
                "후기 작성 마지막 날입니다.",
                "오늘이 후기를 작성할 수 있는 마지막 날입니다. 서둘러 작성해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 11. 후기 작성 포인트 지급 알림 - 후기 작성자에게
    @Override
    public void sendReviewPoint(Long userId, Long reviewId) {
        publish(userId, NotificationType.REVIEW_REWARD,
                "후기 작성 포인트가 지급되었습니다.",
                "후기 작성이 완료되었습니다. 50포인트가 지급되었습니다.",
                RelatedDomain.POINT, reviewId);
    }

    // 12. 매너 온도 상승 알림 - 후기 대상자에게
    // relatedId = null → 클릭 시 마이페이지로 이동 (프론트에서 처리)
    @Override
    public void sendMannerTemperatureChanged(Long userId) {
        publish(userId, NotificationType.MANNER_TEMPERATURE_CHANGED,
                "매너 온도가 올랐습니다.",
                "새로운 후기가 작성되어 매너 온도가 변경되었습니다. 마이페이지에서 확인해 보세요.",
                RelatedDomain.SYSTEM, null);
    }

    // ── 채팅 / 장소 인증 ──────────────────────────────────────────────────

    // 13. 채팅 메시지 수신 알림 - 메시지 수신자에게
    @Override
    public void sendChatReceived(Long userId, Long chatRoomId) {
        publish(userId, NotificationType.CHAT_RECEIVED,
                "새로운 채팅 메시지가 도착했습니다.",
                "새로운 채팅 메시지가 도착했습니다.",
                RelatedDomain.CHAT, chatRoomId);
    }

    // 14. 장소 인증 완료 알림
    // 1:1: 상대방에게 / 그룹: 모임 참여자 전원에게 (호출하는 쪽에서 수신자 분기 처리)
    @Override
    public void sendPlaceVerified(Long userId, Long matchId) {
        publish(userId, NotificationType.PLACE_VERIFIED,
                "장소 인증이 완료되었습니다.",
                "상대방이 장소 인증을 완료했습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 15. 그룹 채팅방 신청자 퇴장 알림 - 등록자에게만
    @Override
    public void sendChatMemberLeft(Long userId, Long chatRoomId) {
        publish(userId, NotificationType.CHAT_MEMBER_LEFT,
                "신청자가 채팅방에서 퇴장했습니다.",
                "신청자가 채팅방에서 퇴장했습니다.",
                RelatedDomain.CHAT, chatRoomId);
    }

    // ── 노쇼 ──────────────────────────────────────────────────────────────

    // 16. 노쇼 예정 알림 - 노쇼 예정 유저에게
    @Override
    public void sendNoShowWarning(Long userId, Long matchId) {
        publish(userId, NotificationType.NO_SHOW_WARNING,
                "노쇼 예정 상태입니다.",
                "노쇼 예정 상태입니다. 24시간 내 이의제기가 없을 경우 예치 포인트가 차감됩니다.",
                RelatedDomain.MEET, matchId);
    }

    // 17. 노쇼 확정 알림 - 관련 사용자 양측에게
    @Override
    public void sendNoShowConfirmed(Long userId, Long matchId) {
        publish(userId, NotificationType.NO_SHOW_CONFIRMED,
                "노쇼가 확정되었습니다.",
                "노쇼가 확정되었습니다. 관련 내용을 확인해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // ── 만남 시간 연장 ────────────────────────────────────────────────────

    // 18. 만남 시간 연장 요청 알림 - 만남 상대방에게
    @Override
    public void sendMeetExtendRequested(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_EXTEND_REQUESTED,
                "만남 시간 연장 요청이 왔습니다.",
                "상대방이 만남 시간 연장을 요청했습니다. 5분 안에 응답해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 19. 만남 시간 연장 수락 알림 - 연장 요청자에게
    @Override
    public void sendMeetExtendAccepted(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_EXTEND_ACCEPTED,
                "만남 시간 연장이 수락되었습니다.",
                "만남 시간 연장이 수락되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 20. 만남 시간 연장 거절 알림 - 연장 요청자에게
    @Override
    public void sendMeetExtendRejected(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_EXTEND_REJECTED,
                "만남 시간 연장이 거절되었습니다.",
                "만남 시간 연장이 거절되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 21. 만남 시간 연장 만료 알림 - 연장 요청자에게
    @Override
    public void sendMeetExtendExpired(Long userId, Long matchId) {
        publish(userId, NotificationType.MEET_EXTEND_EXPIRED,
                "만남 시간 연장 요청이 만료되었습니다.",
                "만남 시간 연장 요청이 만료되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    // 22. 이의제기 접수 알림 - 관리자에게
    @Override
    public void sendDisputeSubmitted(Long adminId, Long disputeId) {
        publish(adminId, NotificationType.DISPUTE_SUBMITTED,
                "새로운 이의제기가 접수되었습니다.",
                "새로운 이의제기가 접수되었습니다.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 23. 이의제기 판정 결과 알림 - 이의제기 신청자에게
    // ACCEPTED / PARTIALLY_ACCEPTED / REJECTED / 자동거절 모두 이 메서드 사용
    @Override
    public void sendDisputeResult(Long userId, Long disputeId) {
        publish(userId, NotificationType.DISPUTE_RESULT,
                "이의제기 판정 결과가 등록되었습니다.",
                "이의제기 판정 결과가 등록되었습니다. 확인해 주세요.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 24. 이의제기 보류 알림 - 이의제기 신청자에게
    @Override
    public void sendDisputePending(Long userId, Long disputeId) {
        publish(userId, NotificationType.DISPUTE_PENDING,
                "이의제기가 보류 처리되었습니다.",
                "이의제기가 보류 처리되었습니다. 24시간 이내에 추가 증거를 제출해 주세요.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 25. 이의제기 추가 증빙 마감 임박 알림 - 이의제기 신청자에게
    // HOLD 판정 후 23시간 경과 시 발송
    @Override
    public void sendDisputeDeadlineReminder(Long userId, Long disputeId) {
        publish(userId, NotificationType.DISPUTE_DEADLINE_REMINDER,
                "이의제기 추가 증빙자료 제출 마감이 임박했습니다.",
                "이의제기 추가 증빙자료 제출 마감이 1시간 남았습니다.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // ── 신고 ──────────────────────────────────────────────────────────────

    // 26. 신고 접수 알림 - 관리자에게
    @Override
    public void sendReportSubmitted(Long adminId, Long reportId) {
        publish(adminId, NotificationType.REPORT_SUBMITTED,
                "새로운 신고가 접수되었습니다.",
                "새로운 신고가 접수되었습니다. 검토해 주세요.",
                RelatedDomain.REPORT, reportId);
    }

    // 27. 신고 채택 포인트 지급 알림 - 신고자에게
    @Override
    public void sendReportAcceptedPoint(Long userId, Long reportId) {
        publish(userId, NotificationType.REPORT_REWARD,
                "신고가 채택되었습니다.",
                "신고가 채택되었습니다. 50포인트가 지급되었습니다.",
                RelatedDomain.REPORT, reportId);
    }

    // 28. 신고 기각 알림 - 신고자에게
    @Override
    public void sendReportRejected(Long userId, Long reportId) {
        publish(userId, NotificationType.REPORT_REJECTED,
                "신고가 기각되었습니다.",
                "접수하신 신고가 검토 결과 기각되었습니다.",
                RelatedDomain.REPORT, reportId);
    }

    // ── 결제 ──────────────────────────────────────────────────────────────

    // 29. 결제 성공 알림 - 결제 사용자에게
    @Override
    public void sendPaymentSuccess(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_SUCCESS,
                "결제가 완료되었습니다.",
                "결제가 완료되었습니다. 포인트가 지급되었습니다.",
                RelatedDomain.POINT, paymentId);
    }

    // 30. 결제 실패 알림 - 결제 사용자에게
    @Override
    public void sendPaymentFailed(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_FAILED,
                "결제에 실패했습니다.",
                "결제에 실패했습니다. 다시 시도해 주세요.",
                RelatedDomain.POINT, paymentId);
    }

    // 31. 결제 취소 및 환불 완료 알림 - 결제 사용자에게
    @Override
    public void sendPaymentCancelSuccess(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_CANCEL_SUCCESS,
                "결제 취소 및 환불이 완료되었습니다.",
                "결제 취소 및 환불이 완료되었습니다.",
                RelatedDomain.POINT, paymentId);
    }

    // 32. 결제 취소 및 환불 실패 알림 - 결제 사용자에게
    @Override
    public void sendPaymentCancelFailed(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_CANCEL_FAILED,
                "결제 취소 및 환불에 실패했습니다.",
                "결제 취소 및 환불에 실패했습니다. 고객센터로 문의해 주세요.",
                RelatedDomain.POINT, paymentId);
    }

    // ── 문의 ──────────────────────────────────────────────────────────────

    // 33. 문의 접수 알림 - 관리자에게
    @Override
    public void sendInquirySubmitted(Long adminId, Long inquiryId) {
        publish(adminId, NotificationType.INQUIRY_SUBMITTED,
                "새로운 문의가 접수되었습니다.",
                "새로운 문의가 접수되었습니다. 검토해 주세요.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // 34. 문의 답변 완료 알림 - 문의 작성자에게
    @Override
    public void sendInquiryAnswered(Long userId, Long inquiryId) {
        publish(userId, NotificationType.INQUIRY_ANSWERED,
                "문의에 대한 답변이 완료되었습니다.",
                "문의에 대한 답변이 완료되었습니다.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // ── 계정 ──────────────────────────────────────────────────────────────

    // 35. 계정 정지 알림 - 해당 사용자에게
    // 제재 단계별 메시지는 호출하는 쪽(Service)에서 title/content를 분기하여 전달
    @Override
    public void sendAccountSuspended(Long userId, String title, String content) {
        publish(userId, NotificationType.ACCOUNT_SUSPENDED,
                title, content,
                RelatedDomain.ACCOUNT, null);
    }

    // 36. 계정 정지 해제 알림 - 해당 사용자에게
    @Override
    public void sendAccountUnsuspended(Long userId) {
        publish(userId, NotificationType.ACCOUNT_UNSUSPENDED,
                "계정 정지가 해제되었습니다.",
                "계정 정지가 해제되었습니다. 다시 서비스를 이용하실 수 있습니다.",
                RelatedDomain.ACCOUNT, null);
    }

    // ── 게시글 / 신고로 인한 경고 ────────────────────────────────────────

    // 37. 게시글 신고 경고 1회 알림 - 게시글 작성자에게
    // 제재 단계별 메시지는 호출하는 쪽(Service)에서 title/content를 분기하여 전달
    @Override
    public void sendPostWarned(Long userId, String title, String content) {
        publish(userId, NotificationType.POST_WARNED_1,
                title, content,
                RelatedDomain.ACCOUNT, null);
    }

    // 38. 게시글 만료 알림 - 게시글 작성자에게
    @Override
    public void sendPostExpired(Long userId, Long postId) {
        publish(userId, NotificationType.POST_EXPIRED,
                "게시글이 만료되었습니다.",
                "약속시간이 지나감에 따라 게시글이 만료되었습니다.",
                RelatedDomain.POST, postId);
    }

    // 39. 게시글 삭제 알림 - 게시글 작성자에게
    // content는 호출하는 쪽(Service)에서 상황에 맞게 전달
    // ex) 관리자 강제 삭제: "해당 게시물이 신고 접수 및 관리자 판단에 의해 삭제되었습니다."
    @Override
    public void sendPostDeleted(Long userId, Long postId) {
        publish(userId, NotificationType.POST_DELETED,
                "게시글이 삭제되었습니다.",
                "해당 게시물이 신고 접수 및 관리자 판단에 의해 삭제되었습니다. 자세한 사항은 고객센터를 확인해 주세요.",
                RelatedDomain.POST, postId);
    }

    // 40. 게시글 복구 알림 - 게시글 작성자에게
    @Override
    public void sendPostRestored(Long userId, Long postId) {
        publish(userId, NotificationType.POST_RESTORED,
                "게시글이 복구되었습니다.",
                "관리자에 의해 삭제되었던 게시물이 복구되어, 예치 포인트가 다시 차감되었습니다.",
                RelatedDomain.POST, postId);
    }
}