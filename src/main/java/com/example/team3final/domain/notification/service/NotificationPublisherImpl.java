package com.example.team3final.domain.notification.service;

import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.notification.dto.event.NotificationEvent;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import com.example.team3final.domain.notification.validation.NotificationEventValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    // Producer와 Consumer가 동일한 알림 불변 조건을 검증한다.
    private final NotificationEventValidator notificationEventValidator;

    // ==================== 공통 메서드 ====================

    // 알림 이벤트 발행 공통 메서드
    // private: sendXxx() 메서드에서만 사용하는 내부 헬퍼
    private void publish(Long receiverId, NotificationType type, String title,
                         String content, RelatedDomain relatedDomain, Long relatedId) {
        publish(receiverId, NotificationReceiverType.USER, type, title, content, relatedDomain, relatedId);
    }

    private void publishAdmin(Long adminId, NotificationType type, String title,
                              String content, RelatedDomain relatedDomain, Long relatedId) {
        publish(adminId, NotificationReceiverType.ADMIN, type, title, content, relatedDomain, relatedId);
    }

    private void publish(Long receiverId, NotificationReceiverType receiverType,
                         NotificationType type, String title, String content,
                         RelatedDomain relatedDomain, Long relatedId) {
        // Consumer 멱등성 처리에 사용할 이벤트 고유 ID를 생성한다.
        String eventId = UUID.randomUUID().toString();

        NotificationEvent event = new NotificationEvent(
                eventId,
                receiverId,
                receiverType,
                type,
                title,
                content,
                relatedDomain,
                relatedId
        );

        // Kafka 발행 전에 알림 도메인의 불변 조건을 검증한다.
        // 검증 실패는 호출자에게 즉시 전달해 잘못된 비즈니스 호출을 빠르게 발견한다.
        notificationEventValidator.validate(event);

        // 직렬화도 트랜잭션 안에서 미리 수행해 실패 시 비즈니스 작업을 롤백할 수 있게 한다.
        String message = serialize(event);

        // USER와 ADMIN의 숫자 ID가 같아도 Kafka key가 충돌하지 않도록 타입을 포함한다.
        String key = receiverType + ":" + receiverId;

        if (isTransactionActive()) {
            // 비즈니스 트랜잭션이 성공적으로 커밋된 경우에만 Kafka 알림을 발행한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendToKafka(event, key, message);
                }
            });
            return;
        }

        // 스케줄러나 이미 커밋 이후인 호출은 즉시 Kafka에 발행한다.
        sendToKafka(event, key, message);
    }

    private boolean isTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private String serialize(NotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 직렬화 실패를 로그만 남기고 숨기지 않는다.
            throw new IllegalStateException(
                    "알림 이벤트 직렬화에 실패했습니다. eventId=" + event.eventId(),
                    e
            );
        }
    }

    private void sendToKafka(NotificationEvent event, String key, String message) {
        // Kafka 전송 결과를 비동기로 확인한다.
        // 정상 토픽 발행 실패 시 원본 메시지를 Producer DLQ로 전달한다.
        kafkaTemplate.send(KafkaTopics.NOTIFICATIONS, key, message)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        log.info(
                                "[Kafka Notification Producer] 알림 이벤트 발행 성공"
                                        + " - eventId: {}, receiverType: {}, receiverId: {}, type: {}, relatedId: {}",
                                event.eventId(),
                                event.receiverType(),
                                event.receiverId(),
                                event.type(),
                                event.relatedId()
                        );
                        return;
                    }

                    log.error(
                            "[Kafka Notification Producer] 알림 이벤트 발행 실패 → DLQ 발행"
                                    + " - eventId: {}, receiverType: {}, receiverId: {}, type: {}",
                            event.eventId(),
                            event.receiverType(),
                            event.receiverId(),
                            event.type(),
                            exception
                    );

                    // DLQ 전송도 비동기이므로 최종 실패 여부를 로그로 남긴다.
                    kafkaTemplate.send(KafkaTopics.NOTIFICATIONS_DLQ, key, message)
                            .whenComplete((dlqResult, dlqException) -> {
                                if (dlqException != null) {
                                    log.error(
                                            "[Kafka Notification Producer] DLQ 발행 실패"
                                                    + " - eventId: {}, receiverId: {}, type: {}",
                                            event.eventId(),
                                            event.receiverId(),
                                            event.type(),
                                            dlqException
                                    );
                                }
                            });
                });
    }

    // ==================== NotificationPublisher 구현 ====================

    // ── 매칭 ──────────────────────────────────────────────────────────────

    // 1. 게시글 신청 알림 - HOST에게
    @Async
    @Override
    public void sendMatchApplied(Long userId, Long matchId) {
        publish(userId, NotificationType.MATCH_APPLIED,
                "새로운 신청자가 있습니다.",
                "게시글에 새로운 신청자가 있습니다. 신청 내용을 확인해 주세요.",
                RelatedDomain.MATCH, matchId);
    }

    // 2. 매칭 확정 알림 - 등록자에게
    @Async
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
                "만남 시간이 10분 지났습니다. 서둘러 장소인증을 완료해주세요.",
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
    public void sendPlaceVerified(Long userId, Long matchId, String verifierNickname) {
        String nickname = verifierNickname == null || verifierNickname.isBlank() ? "상대방" : verifierNickname;
        publish(userId, NotificationType.PLACE_VERIFIED,
                nickname + "님의 장소 인증이 완료되었습니다.",
                nickname + "님의 장소 인증이 완료되었습니다.",
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
                "매칭 #" + matchId + "노쇼 예정 상태입니다.",
                "노쇼 예정 상태입니다. 24시간 내 이의제기가 없을 경우 예치 포인트가 차감됩니다.",
                RelatedDomain.MEET, matchId);
    }

    // 17. 상대방 노쇼 예정 알림 - 정상 도착/대기 중인 상대방에게
    @Override
    public void sendOpponentNoShowWarning(Long userId, Long matchId, String noShowNickname) {
        publish(userId, NotificationType.OPPONENT_NO_SHOW_WARNING,
                noShowNickname + "님이 노쇼 예정입니다.",
                noShowNickname + "님이 노쇼 예정 상태입니다. 매칭 상세를 확인해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 18. 노쇼 확정 알림 - 관련 사용자 양측에게
    @Override
    public void sendNoShowConfirmed(Long userId, Long matchId) {
        publish(userId, NotificationType.NO_SHOW_CONFIRMED,
                "노쇼가 확정되었습니다.",
                "노쇼가 확정되었습니다. 자세한 내용은 문의해주세요.",
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
        publishAdmin(adminId, NotificationType.DISPUTE_SUBMITTED,
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
                "이의제기가 보류 처리되었습니다. 24시간 이내에 추가 증빙자료를 제출해 주세요.",
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
        publishAdmin(adminId, NotificationType.REPORT_SUBMITTED,
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

    // 28. 신고는 채택됐지만 월 보상 한도로 포인트 미지급 알림 - 신고자에게
    @Override
    public void sendReportAcceptedWithoutPoint(Long userId, Long reportId) {
        publish(
                userId,
                NotificationType.REPORT_REWARD,
                "신고가 채택되었습니다.",
                "신고가 채택되었으나 월 보상 한도에 도달하여 포인트는 지급되지 않았습니다.",
                RelatedDomain.REPORT,
                reportId
        );
    }

    // 29. 신고 기각 알림 - 신고자에게
    @Override
    public void sendReportRejected(Long userId, Long reportId) {
        publish(userId, NotificationType.REPORT_REJECTED,
                "신고가 기각되었습니다.",
                "접수하신 신고가 검토 결과 기각되었습니다.",
                RelatedDomain.REPORT, reportId);
    }

    // ── 결제 ──────────────────────────────────────────────────────────────

    // 30. 결제 성공 알림 - 결제 사용자에게
    @Override
    public void sendPaymentSuccess(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_SUCCESS,
                "결제가 완료되었습니다.",
                "결제가 완료되었습니다. 포인트가 지급되었습니다.",
                RelatedDomain.POINT, paymentId);
    }

    // 31. 결제 실패 알림 - 결제 사용자에게
    @Override
    public void sendPaymentFailed(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_FAILED,
                "결제에 실패했습니다.",
                "결제에 실패했습니다. 다시 시도해 주세요.",
                RelatedDomain.POINT, paymentId);
    }

    // 32. 결제 취소 및 환불 완료 알림 - 결제 사용자에게
    @Override
    public void sendPaymentCancelSuccess(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_CANCEL_SUCCESS,
                "결제 취소 및 환불이 완료되었습니다.",
                "결제 취소 및 환불이 완료되었습니다.",
                RelatedDomain.POINT, paymentId);
    }

    // 33. 결제 취소 및 환불 실패 알림 - 결제 사용자에게
    @Override
    public void sendPaymentCancelFailed(Long userId, Long paymentId) {
        publish(userId, NotificationType.PAYMENT_CANCEL_FAILED,
                "결제 취소 및 환불에 실패했습니다.",
                "결제 취소 및 환불에 실패했습니다. 고객센터로 문의해 주세요.",
                RelatedDomain.POINT, paymentId);
    }

    // ── 문의 ──────────────────────────────────────────────────────────────

    // 34. 문의 접수 알림 - 관리자에게
    @Override
    public void sendInquirySubmitted(Long adminId, Long inquiryId) {
        publishAdmin(adminId, NotificationType.INQUIRY_SUBMITTED,
                "새로운 문의가 접수되었습니다.",
                "새로운 문의가 접수되었습니다. 검토해 주세요.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // 35. 문의 답변 완료 알림 - 문의 작성자에게
    @Override
    public void sendInquiryAnswered(Long userId, Long inquiryId) {
        publish(userId, NotificationType.INQUIRY_ANSWERED,
                "문의에 대한 답변이 완료되었습니다.",
                "문의에 대한 답변이 완료되었습니다.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // ── 계정 ──────────────────────────────────────────────────────────────

    // 36. 계정 정지 알림 - 해당 사용자에게
    // 제재 단계별 메시지는 호출하는 쪽(Service)에서 title/content를 분기하여 전달
    @Override
    public void sendAccountSuspended(Long userId, String title, String content) {
        publish(userId, NotificationType.ACCOUNT_SUSPENDED,
                title, content,
                RelatedDomain.ACCOUNT, null);
    }

    // 37. 계정 정지 해제 알림 - 해당 사용자에게
    @Override
    public void sendAccountUnsuspended(Long userId, String reason) {
        String content = reason == null || reason.isBlank()
                ? "계정 정지가 해제되었습니다. 다시 서비스를 이용하실 수 있습니다."
                : "계정 정지가 해제되었습니다. 사유: " + reason;
        publish(userId, NotificationType.ACCOUNT_UNSUSPENDED,
                "계정 정지가 해제되었습니다.",
                content,
                RelatedDomain.ACCOUNT, null);
    }

    // ── 게시글 / 신고로 인한 경고 ────────────────────────────────────────

    // 38. 게시글 신고 경고 1회 알림 - 게시글 작성자에게
    // 제재 단계별 메시지는 호출하는 쪽(Service)에서 title/content를 분기하여 전달
    @Override
    public void sendPostWarned(Long userId, String title, String content) {
        publish(userId, NotificationType.POST_WARNED_1,
                title, content,
                RelatedDomain.ACCOUNT, null);
    }

    // 39. 게시글 만료 알림 - 게시글 작성자에게
    @Override
    public void sendPostExpired(Long userId, Long postId) {
        publish(userId, NotificationType.POST_EXPIRED,
                "게시글이 만료되었습니다.",
                "약속시간이 지나감에 따라 게시글이 만료되었습니다.",
                RelatedDomain.POST, postId);
    }

    // 40. 게시글 삭제 알림 - 게시글 작성자에게
    // content는 호출하는 쪽(Service)에서 상황에 맞게 전달
    // ex) 관리자 강제 삭제: "해당 게시물이 신고 접수 및 관리자 판단에 의해 삭제되었습니다."
    @Override
    public void sendPostDeleted(Long userId, Long postId) {
        publish(userId, NotificationType.POST_DELETED,
                "게시글이 삭제되었습니다.",
                "해당 게시물이 신고 접수 및 관리자 판단에 의해 삭제되었습니다.",
                RelatedDomain.POST, postId);
    }

    // 41. 게시글 복구 알림 - 게시글 작성자에게
    @Override
    public void sendPostRestored(Long userId, Long postId) {
        publish(userId, NotificationType.POST_RESTORED,
                "게시글이 복구되었습니다.",
                "삭제되었던 게시물이 복구됨으로 예치 포인트가 차감되었습니다.",
                RelatedDomain.POST, postId);
    }
}
