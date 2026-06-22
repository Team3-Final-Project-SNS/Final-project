package com.example.team3final.domain.notification.dto.event;

import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Kafka로 전달할 알림 이벤트 DTO
// Producer는 이 DTO를 JSON으로 직렬화해서 발행하고,
// Consumer는 JSON을 다시 이 DTO로 역직렬화한 뒤 DB 저장 + SSE 전송을 처리한다.
// [변경] 멱등성 구현을 위해 eventId 필드 추가
// eventId: 이 이벤트를 전 세계에서 유일하게 식별하는 UUID 문자열
//          Consumer가 이 ID를 Redis에 기록해 중복 처리를 방지한다.
public record NotificationEvent(

        // [신규] 이벤트 고유 식별자 - Producer가 UUID로 생성해서 넣어준다
        @JsonProperty("eventId") String eventId,

        @JsonProperty("receiverId") Long receiverId,
        @JsonProperty("receiverType") NotificationReceiverType receiverType,
        @JsonProperty("type") NotificationType type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("relatedDomain") RelatedDomain relatedDomain,
        @JsonProperty("relatedId") Long relatedId
) {
    // Jackson이 JSON -> record 역직렬화할 때 이 생성자를 사용
    @JsonCreator
    public NotificationEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("receiverId") Long receiverId,
            @JsonProperty("receiverType") NotificationReceiverType receiverType,
            @JsonProperty("type") NotificationType type,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("relatedDomain") RelatedDomain relatedDomain,
            @JsonProperty("relatedId") Long relatedId
    ) {
        this.eventId = eventId;
        this.receiverId = receiverId;
        // 누락된 receiverType은 Validator에서 잘못된 이벤트로 처리한다.
        this.receiverType = receiverType;
        this.type = type;
        this.title = title;
        this.content = content;
        this.relatedDomain = relatedDomain;
        this.relatedId = relatedId;
    }
}
