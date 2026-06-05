package com.example.team3final.common.kafka;

public final class KafkaTopics {

    // ==================== 기존 토픽 ====================

    // 채팅 메시지 토픽
    public static final String CHAT_MESSAGES = "chat-messages";

    // 알림 이벤트 토픽
    public static final String NOTIFICATIONS = "notifications";

    // ==================== [신규] 실패 대응 토픽 ====================

    // DLQ (Dead Letter Queue)
    // Producer가 Kafka에 메시지 전송을 끝내 실패했을 때 보관하는 토픽
    // 네이밍 컨벤션: 원본토픽 + ".dlq"
    public static final String NOTIFICATIONS_DLQ = "notifications.dlq";
    public static final String CHAT_MESSAGES_DLQ = "chat-messages.dlq";

    // DLT (Dead Letter Topic)
    // Consumer가 메시지 처리를 끝내 실패했을 때 보관하는 토픽
    // Spring Kafka 기본 네이밍: 원본토픽 + ".DLT"
    public static final String NOTIFICATIONS_DLT = "notifications.DLT";
    public static final String CHAT_MESSAGES_DLT = "chat-messages.DLT";

    private KafkaTopics() {
    }
}