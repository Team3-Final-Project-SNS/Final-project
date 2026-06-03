package com.example.team3final.domain.notification.dto.event;

import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;

// Kafka로 전달할 알림 이벤트 DTO
// Producer는 이 DTO를 JSON으로 직렬화해서 발행하고,
// Consumer는 JSON을 다시 이 DTO로 역직렬화한 뒤 DB 저장 + SSE 전송을 처리한다.
public record NotificationEvent(
        Long receiverId,
        NotificationType type,
        String title,
        String content,
        RelatedDomain relatedDomain,
        Long relatedId
) {
}