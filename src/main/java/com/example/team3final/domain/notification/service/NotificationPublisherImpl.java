package com.example.team3final.domain.notification.service;

import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// 알림 발송 구현체 (NotificationPublisher 인터페이스 구현)
// @Transactional: readOnly=false (알림 저장 INSERT 발생)
// @Async: 각 sendXxx() 메서드에서 비동기 처리
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationPublisherImpl implements NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;

    // ==================== 공통 메서드 ====================

    // 알림 저장 + SSE 전송 공통 메서드
    // private: sendXxx() 메서드에서만 사용하는 내부 헬퍼
    private void saveAndSend(Long receiverId, NotificationType type, String title,
                            String content, RelatedDomain relatedDomain, Long relatedId) {

        // DB에 알림 저장
        Notification notification = Notification.builder()
                .receiverId(receiverId)       // 수신 유저 ID
                .type(type)                   // 알림 유형 (NotificationType)
                .title(title)                 // 알림 제목
                .content(content)             // 알림 내용
                .relatedDomain(relatedDomain) // 연관 도메인 (클릭 시 이동할 화면 도메인)
                .relatedId(relatedId)         // 연관 도메인 ID (클릭 시 이동할 화면 ID, null 가능)
                .build();
        notificationRepository.save(notification);

        // SSE로 실시간 전송 (연결된 유저만)
        // ifPresent: Emitter가 있을 때만 전송 (연결 안 됐으면 스킵)
        sseEmitterRepository.findByUserId(receiverId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(GetNotificationsResponseDto.from(notification)));
            } catch (IOException e) {
                // 전송 실패 시 Emitter 삭제 (끊어진 연결 정리)
                sseEmitterRepository.deleteByUserId(receiverId);
                log.warn("[SSE] 알림 전송 실패 - userId: {}", receiverId);
            }
        });
    }

    // ==================== NotificationPublisher 구현 ====================

    // 1. 게시글 신청 알림
    @Override
    @Async
    public void sendMatchApplied(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MATCH_APPLIED,
                "새로운 신청자가 있습니다.",
                "게시글에 새로운 신청자가 있습니다. 신청 내용을 확인해 주세요.",
                RelatedDomain.MATCH, matchId);
    }

    // 2. 매칭 취소 알림
    @Override
    @Async
    public void sendMatchCancelled(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MATCH_CANCELLED,
                "신청이 취소되었습니다.",
                "신청자가 참여 신청을 취소했습니다.",
                RelatedDomain.MATCH, matchId);
    }

    // 3. 채팅 메시지 수신 알림
    @Override
    @Async
    public void sendChatReceived(Long userId, Long chatRoomId) {
        saveAndSend(userId, NotificationType.CHAT_RECEIVED,
                "새로운 채팅 메시지가 도착했습니다.",
                "새로운 채팅 메시지가 도착했습니다.",
                RelatedDomain.CHAT, chatRoomId);
    }

    // 4. 장소 인증 완료 알림
    @Override
    @Async
    public void sendPlaceVerified(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.PLACE_VERIFIED,
                "장소 인증이 완료되었습니다.",
                "상대방이 장소 인증을 완료했습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 5. 만남 시간 임박 알림
    @Override
    @Async
    public void sendMeetImminent(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_IMMINENT,
                "만남 시간이 곧 다가옵니다.",
                "만남 시간이 곧 다가옵니다. 준비해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 6. 만남 30분 전 알림
    @Override
    @Async
    public void sendMeetReminder30(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_REMINDER,
                "만남 시간이 30분 남았습니다.",
                "만남 시간이 30분 남았습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 7. 만남 15분 전 알림
    @Override
    @Async
    public void sendMeetReminder15(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_REMINDER,
                "만남 시간이 15분 남았습니다.",
                "만남 시간이 15분 남았습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 8. 노쇼 확정 알림
    @Override
    @Async
    public void sendNoShowConfirmed(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.NO_SHOW_CONFIRMED,
                "노쇼가 확정되었습니다.",
                "노쇼가 확정되었습니다. 관련 내용을 확인해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 9. 노쇼 예정 알림
    @Override
    @Async
    public void sendNoShowWarning(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.NO_SHOW_WARNING,
                "노쇼 예정 상태입니다.",
                "장소를 이탈했습니다. 30분 뒤에 장소에 없거나 만남인증이 완료되지 않을 시 노쇼 확정입니다.",
                RelatedDomain.MEET, matchId);
    }

    // 10. 신고 처리 결과 알림
    @Override
    @Async
    public void sendReportResult(Long userId, Long reportId) {
        saveAndSend(userId, NotificationType.REPORT_RESULT,
                "신고 처리 결과가 등록되었습니다.",
                "신고 처리 결과가 등록되었습니다. 확인해 주세요.",
                RelatedDomain.REPORT, reportId);
    }

    // 11. 이의제기 접수 알림 - 관리자에게
    @Override
    @Async
    public void sendDisputeSubmitted(Long adminId, Long disputeId) {
        saveAndSend(adminId, NotificationType.DISPUTE_SUBMITTED,
                "새로운 이의제기가 접수되었습니다.",
                "새로운 이의제기가 접수되었습니다.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 12. 시스템 공지 알림
    // 용도: 공지 / 게시글 삭제 안내 / 게시글 만료 안내 / 제재 안내
    @Override
    @Async
    public void sendSystem(Long userId, String title, String content) {
        saveAndSend(userId, NotificationType.SYSTEM,
                title, content,
                RelatedDomain.SYSTEM, null);
    }

    // 13. 신고 채택 포인트 지급 알림
    @Override
    @Async
    public void sendReportAcceptedPoint(Long userId, Long reportId) {
        saveAndSend(userId, NotificationType.POINT_CHANGED,
                "신고가 채택되었습니다.",
                "신고가 채택되었습니다. 50포인트가 지급되었습니다.",
                RelatedDomain.REPORT, reportId);
    }

    // 14. 후기 작성 포인트 지급 알림
    @Override
    @Async
    public void sendReviewPoint(Long userId, Long reviewId) {
        saveAndSend(userId, NotificationType.POINT_CHANGED,
                "후기가 작성되었습니다.",
                "후기가 작성되었습니다. 50포인트가 지급되었습니다.",
                RelatedDomain.POINT, reviewId);
    }

    // 15. 문의 답변 완료 알림
    @Override
    @Async
    public void sendInquiryAnswered(Long userId, Long inquiryId) {
        saveAndSend(userId, NotificationType.INQUIRY_ANSWERED,
                "문의에 대한 답변이 완료되었습니다.",
                "문의에 대한 답변이 완료되었습니다.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // 16. 매칭 확정 알림
    @Override
    @Async
    public void sendMatchConfirmed(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MATCH_CONFIRMED,
                "매칭이 확정되었습니다.",
                "매칭이 확정되었습니다. 채팅방을 확인해 주세요.",
                RelatedDomain.MATCH, matchId);
    }

    // 17. 이의제기 판정 결과 알림
    // 용도: 관리자 승인/거절 시 + 보류 24시간 초과 자동 거절 시 발송
    @Override
    @Async
    public void sendDisputeResult(Long userId, Long disputeId) {
        saveAndSend(userId, NotificationType.DISPUTE_RESULT,
                "이의제기 판정 결과가 등록되었습니다.",
                "이의제기 판정 결과가 등록되었습니다. 확인해 주세요.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 18. 만남 시간 연장 요청 알림
    @Override
    @Async
    public void sendMeetExtendRequested(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_EXTEND_REQUESTED,
                "만남 시간 연장 요청이 왔습니다.",
                "상대방이 만남 시간 연장을 요청했습니다. 5분 안에 응답해 주세요.",
                RelatedDomain.MEET, matchId);
    }

    // 19. 만남 시간 연장 수락 알림
    @Override
    @Async
    public void sendMeetExtendAccepted(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_EXTEND_ACCEPTED,
                "만남 시간 연장이 수락되었습니다.",
                "만남 시간 연장이 수락되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 20. 만남 시간 연장 거절 알림
    @Override
    @Async
    public void sendMeetExtendRejected(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_EXTEND_REJECTED,
                "만남 시간 연장이 거절되었습니다.",
                "만남 시간 연장이 거절되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 21. 만남 시간 연장 만료 알림
    @Override
    @Async
    public void sendMeetExtendExpired(Long userId, Long matchId) {
        saveAndSend(userId, NotificationType.MEET_EXTEND_EXPIRED,
                "만남 시간 연장 요청이 만료되었습니다.",
                "만남 시간 연장 요청이 만료되었습니다.",
                RelatedDomain.MEET, matchId);
    }

    // 24. 이의제기 보류 알림 - 이의제기 신청자에게
    // 관리자가 보류 처리 시 발송
    @Override
    @Async
    public void sendDisputePending(Long userId, Long disputeId) {
        saveAndSend(userId, NotificationType.DISPUTE_PENDING,
                "이의제기가 보류 처리되었습니다.",
                "이의제기가 보류 처리되었습니다. 24시간 이내에 추가 증거를 제출해 주세요.",
                RelatedDomain.DISPUTE, disputeId);
    }

    // 25. 신고 접수 알림 - 관리자에게
    // 신규 신고 접수 시 발송 → 전체 관리자에게 순회 발송
    @Override
    @Async
    public void sendReportSubmitted(Long adminId, Long reportId) {
        saveAndSend(adminId, NotificationType.REPORT_SUBMITTED,
                "새로운 신고가 접수되었습니다.",
                "새로운 신고가 접수되었습니다. 검토해 주세요.",
                RelatedDomain.REPORT, reportId);
    }

    // 26. 문의 접수 알림 - 관리자에게
    // 신규 문의 접수 시 발송 → 전체 관리자에게 순회 발송
    @Override
    @Async
    public void sendInquirySubmitted(Long adminId, Long inquiryId) {
        saveAndSend(adminId, NotificationType.INQUIRY_SUBMITTED,
                "새로운 문의가 접수되었습니다.",
                "새로운 문의가 접수되었습니다. 검토해 주세요.",
                RelatedDomain.INQUIRY, inquiryId);
    }

    // 27. 매너 온도 변경 알림 - 후기 대상자에게
    // 후기 작성으로 매너 온도가 변경되었을 때 발송
    // relatedId = null → 클릭 시 마이페이지로 이동 (프론트에서 처리)
    @Override
    @Async
    public void sendMannerTemperatureChanged(Long userId) {
        saveAndSend(userId, NotificationType.MANNER_TEMPERATURE_CHANGED,
                "매너 온도가 변경되었습니다.",
                "새로운 후기가 작성되어 매너 온도가 변경되었습니다. 마이페이지에서 확인해 보세요.",
                RelatedDomain.SYSTEM, null);
    }
}