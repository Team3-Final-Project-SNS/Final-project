package com.example.team3final.domain.notification.validation;

import com.example.team3final.domain.notification.dto.event.NotificationEvent;
import com.example.team3final.domain.notification.exception.InvalidNotificationEventException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationEventValidator {

    // Notification 엔티티의 DB 컬럼 길이와 동일하게 유지한다.
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int CONTENT_MAX_LENGTH = 500;

    public void validate(NotificationEvent event) {

        // 이벤트 객체 자체가 없으면 이후 필드 검증을 진행할 수 없다.
        if (event == null) {
            throw invalid("알림 이벤트는 필수입니다.");
        }

        // eventId는 Consumer의 Redis 멱등성 키로 사용된다.
        validateEventId(event.eventId());

        // receiverId는 USER 또는 ADMIN의 실제 식별자로 사용된다.
        if (event.receiverId() == null || event.receiverId() <= 0) {
            throw invalid("receiverId는 양수여야 합니다.");
        }

        // 누락된 수신자 타입을 USER로 임의 보정하지 않고 거부한다.
        if (event.receiverType() == null) {
            throw invalid("receiverType은 필수입니다.");
        }

        if (event.type() == null) {
            throw invalid("type은 필수입니다.");
        }

        validateText("title", event.title(), TITLE_MAX_LENGTH);
        validateText("content", event.content(), CONTENT_MAX_LENGTH);

        if (event.relatedDomain() == null) {
            throw invalid("relatedDomain은 필수입니다.");
        }

        // 알림 타입별 불변 정책을 조회한다.
        NotificationPolicy.Policy policy = NotificationPolicy.get(event.type());

        // 신규 타입 추가 시 정책 등록이 누락되는 것을 방지한다.
        if (policy == null) {
            throw invalid("알림 타입 정책이 정의되지 않았습니다: " + event.type());
        }

        // 관리자 알림과 사용자 알림의 수신자 타입 혼동을 방지한다.
        if (policy.receiverType() != event.receiverType()) {
            throw invalid("알림 타입과 수신자 타입이 일치하지 않습니다.");
        }

        // 알림 타입과 화면 이동에 사용하는 연관 도메인이 일치하는지 확인한다.
        if (policy.relatedDomain() != event.relatedDomain()) {
            throw invalid("알림 타입과 연관 도메인이 일치하지 않습니다.");
        }

        validateRelatedId(event, policy);
    }

    private void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw invalid("eventId는 필수입니다.");
        }

        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            throw invalid("eventId는 UUID 형식이어야 합니다.");
        }
    }

    private void validateText(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + "은 비어 있을 수 없습니다.");
        }

        if (value.length() > maxLength) {
            throw invalid(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
    }

    private void validateRelatedId(
            NotificationEvent event,
            NotificationPolicy.Policy policy
    ) {
        if (policy.relatedIdRequired()) {
            if (event.relatedId() == null || event.relatedId() <= 0) {
                throw invalid("해당 알림의 relatedId는 양수여야 합니다.");
            }
            return;
        }

        // relatedId가 필요 없는 알림은 null만 허용한다.
        if (event.relatedId() != null) {
            throw invalid("해당 알림에는 relatedId를 지정할 수 없습니다.");
        }
    }

    private InvalidNotificationEventException invalid(String message) {
        return new InvalidNotificationEventException(message);
    }
}
