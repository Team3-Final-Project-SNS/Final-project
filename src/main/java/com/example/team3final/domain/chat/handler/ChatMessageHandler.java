package com.example.team3final.domain.chat.handler;

import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.dto.request.ChatMessageRequestDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.pubsub.RedisMessagePublisher;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.chat.service.BadWordFilterService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    // SimpMessagingTemplate: 특정 경로를 구독 중인 클라이언트에게 메시지 브로드캐스트
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMemberRepository chatMemberRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final UserService userService;
    private final BadWordFilterService badWordFilterService;     // 욕설 필터링
    private final NotificationPublisher notificationPublisher;   // 알림 발송

    // 메시지 전송
    // 클라이언트가 /pub/chat/rooms/{chatRoomId} 로 메시지 보내면 여기서 처리
    @Transactional
    @MessageMapping("/chat/rooms/{chatRoomId}")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,   // 경로 변수
            @Payload ChatMessageRequestDto request,  // 메시지 내용
            SimpMessageHeaderAccessor headerAccessor // 세션 정보 (JWT에서 저장한 email)
    ) {
        // Handshake 시 저장한 이메일 꺼내기
        var sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.error("[WebSocket] 세션 정보가 없습니다.");
            return;
        }
        String email = (String) sessionAttributes.get("email");

        // 이메일로 userId 조회
        Long senderId = userService.getUserIdByEmail(email);

        // 채팅방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // DEACTIVATED: 완전 비활성화 (매칭 취소) — 메시지 전송/조회 모두 불가
        if (chatRoom.isDeactivated()) {
            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/errors",
                    ErrorCode.CHAT_ROOM_DEACTIVATED.getMessage() // CHAT_004
            );
            return;
        }

        // READ_ONLY: 읽기 전용 (만남 완료 / 노쇼 확정) — 메시지 전송만 불가, 조회는 가능
        if (chatRoom.isReadOnly()) {
            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/errors",
                    ErrorCode.CHAT_ROOM_READ_ONLY.getMessage() // CHAT_003
            );
            return;
        }

        // 채팅방 참여자인지 확인
        if (!chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, senderId)) {
            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/errors",
                    ErrorCode.CHAT_NOT_PARTICIPANT.getMessage()
            );
            return;
        }

        // 욕설 필터링 후 메시지 DB 저장
        String filteredContent = badWordFilterService.filter(request.getContent());

        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .senderId(senderId)
                .content(filteredContent) // 필터링된 메시지 저장
                .build();
        chatMessageRepository.save(chatMessage);

        log.info("[WebSocket] 메시지 저장 - chatRoomId: {}, senderId: {}", chatRoomId, senderId);

        // 채팅방 구독자들에게 메시지 브로드캐스트
        // Redis Publish → RedisMessageSubscriber → WebSocket 전송
        // 서버가 여러 대여도 모든 서버의 구독자에게 메시지 전달 가능
        ChatMessageResponseDto response = new ChatMessageResponseDto(
                chatMessage.getId(),
                chatRoomId,
                senderId,
                userService.getUser(senderId).nickname(),
                chatMessage.getContent(),
                chatMessage.isRead(),
                chatMessage.getCreatedAt()
        );
        redisMessagePublisher.publish(chatRoomId, response);

        // 채팅방 참여자에게 메시지 수신 알림 발송 (발신자 제외)
        chatMemberRepository.findByChatRoomId(chatRoomId).stream()
                .filter(member -> !member.getUserId().equals(senderId)) // 발신자 제외
                .forEach(member ->
                        notificationPublisher.sendChatReceived(member.getUserId(), chatRoomId));

    }
}