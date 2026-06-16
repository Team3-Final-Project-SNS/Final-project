package com.example.team3final.domain.notification.validation;

import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;

import java.util.EnumMap;
import java.util.Map;

public final class NotificationPolicy {

    // 알림 타입별 수신자, 연관 도메인, relatedId 필수 여부를 한 곳에서 관리한다.
    private static final Map<NotificationType, Policy> POLICIES =
            new EnumMap<>(NotificationType.class);

    static {
        // 매칭
        userRequired(NotificationType.MATCH_APPLIED, RelatedDomain.MATCH);
        userRequired(NotificationType.MATCH_CONFIRMED, RelatedDomain.MATCH);
        userRequired(NotificationType.MATCH_CANCELLED, RelatedDomain.MATCH);

        // 만남 시간
        userRequired(NotificationType.MEET_REMINDER, RelatedDomain.MEET);
        userRequired(NotificationType.MEET_IMMINENT, RelatedDomain.MEET);
        userRequired(NotificationType.MEET_OVERDUE, RelatedDomain.MEET);

        // 만남 완료와 후기
        userRequired(NotificationType.MEET_COMPLETED, RelatedDomain.MEET);
        userRequired(NotificationType.REVIEW_DEADLINE_REMINDER, RelatedDomain.MEET);
        userRequired(NotificationType.REVIEW_REWARD, RelatedDomain.POINT);
        userOptional(NotificationType.MANNER_TEMPERATURE_CHANGED, RelatedDomain.SYSTEM);

        // 채팅과 장소 인증
        userRequired(NotificationType.CHAT_RECEIVED, RelatedDomain.CHAT);
        userRequired(NotificationType.PLACE_VERIFIED, RelatedDomain.MEET);
        userRequired(NotificationType.CHAT_MEMBER_LEFT, RelatedDomain.CHAT);

        // 노쇼
        userRequired(NotificationType.NO_SHOW_WARNING, RelatedDomain.MEET);
        userRequired(NotificationType.OPPONENT_NO_SHOW_WARNING, RelatedDomain.MEET);
        userRequired(NotificationType.NO_SHOW_CONFIRMED, RelatedDomain.MEET);

        // 만남 시간 연장
        userRequired(NotificationType.MEET_EXTEND_REQUESTED, RelatedDomain.MEET);
        userRequired(NotificationType.MEET_EXTEND_ACCEPTED, RelatedDomain.MEET);
        userRequired(NotificationType.MEET_EXTEND_REJECTED, RelatedDomain.MEET);
        userRequired(NotificationType.MEET_EXTEND_EXPIRED, RelatedDomain.MEET);

        // 이의제기
        adminRequired(NotificationType.DISPUTE_SUBMITTED, RelatedDomain.DISPUTE);
        userRequired(NotificationType.DISPUTE_RESULT, RelatedDomain.DISPUTE);
        userRequired(NotificationType.DISPUTE_PENDING, RelatedDomain.DISPUTE);
        userRequired(NotificationType.DISPUTE_DEADLINE_REMINDER, RelatedDomain.DISPUTE);

        // 신고
        adminRequired(NotificationType.REPORT_SUBMITTED, RelatedDomain.REPORT);
        userRequired(NotificationType.REPORT_REWARD, RelatedDomain.REPORT);
        userRequired(NotificationType.REPORT_REJECTED, RelatedDomain.REPORT);

        // 결제
        userRequired(NotificationType.PAYMENT_SUCCESS, RelatedDomain.POINT);
        userRequired(NotificationType.PAYMENT_FAILED, RelatedDomain.POINT);
        userRequired(NotificationType.PAYMENT_CANCEL_SUCCESS, RelatedDomain.POINT);
        userRequired(NotificationType.PAYMENT_CANCEL_FAILED, RelatedDomain.POINT);

        // 문의
        adminRequired(NotificationType.INQUIRY_SUBMITTED, RelatedDomain.INQUIRY);
        userRequired(NotificationType.INQUIRY_ANSWERED, RelatedDomain.INQUIRY);

        // 계정
        userOptional(NotificationType.ACCOUNT_SUSPENDED, RelatedDomain.ACCOUNT);
        userOptional(NotificationType.ACCOUNT_UNSUSPENDED, RelatedDomain.ACCOUNT);

        // 게시글과 신고 경고
        userOptional(NotificationType.POST_WARNED_1, RelatedDomain.ACCOUNT);
        userOptional(NotificationType.POST_WARNED_2, RelatedDomain.ACCOUNT);
        userRequired(NotificationType.POST_EXPIRING_SOON, RelatedDomain.POST);
        userRequired(NotificationType.POST_EXPIRED, RelatedDomain.POST);
        userRequired(NotificationType.POST_DELETED, RelatedDomain.POST);
        userRequired(NotificationType.POST_RESTORED, RelatedDomain.POST);
    }

    private NotificationPolicy() {
    }

    public static Policy get(NotificationType type) {
        return POLICIES.get(type);
    }

    private static void userRequired(NotificationType type, RelatedDomain domain) {
        register(type, NotificationReceiverType.USER, domain, true);
    }

    private static void userOptional(NotificationType type, RelatedDomain domain) {
        register(type, NotificationReceiverType.USER, domain, false);
    }

    private static void adminRequired(NotificationType type, RelatedDomain domain) {
        register(type, NotificationReceiverType.ADMIN, domain, true);
    }

    private static void register(
            NotificationType type,
            NotificationReceiverType receiverType,
            RelatedDomain relatedDomain,
            boolean relatedIdRequired
    ) {
        POLICIES.put(
                type,
                new Policy(receiverType, relatedDomain, relatedIdRequired)
        );
    }

    // 하나의 알림 타입에 적용되는 불변 정책
    public record Policy(
            NotificationReceiverType receiverType,
            RelatedDomain relatedDomain,
            boolean relatedIdRequired
    ) {
    }
}
