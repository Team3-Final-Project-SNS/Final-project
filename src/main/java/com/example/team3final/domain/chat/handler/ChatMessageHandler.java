package com.example.team3final.domain.chat.handler;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.chat.dto.request.ChatMessageRequestDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
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
    private final SimpMessagingTemplate messagingTemplate;
    // SimpMessagingTemplate: 특정 경로를 구독 중인 클라이언트에게 메시지 브로드캐스트

    private final UserService userService;

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
        String email = (String) headerAccessor.getSessionAttributes().get("email");

        // 이메일로 userId 조회
        Long senderId = userService.getUserIdByEmail(email);

        // 채팅방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 비활성화된 채팅방은 메시지 전송 불가
        if (!chatRoom.isActive()) {
            // 에러를 보낸 사람에게만 전송
            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/errors",
                    ErrorCode.CHAT_ROOM_INACTIVE.getMessage()
            );
            return;
        }

        // 채팅방 참여자인지 확인
        if (!chatRoom.getAuthorId().equals(senderId) && !chatRoom.getApplicantId().equals(senderId)) {
            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/errors",
                    ErrorCode.CHAT_NOT_PARTICIPANT.getMessage()
            );
            return;
        }

        // 메시지 DB 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .senderId(senderId)
                .content(request.getContent())
                .build();
        chatMessageRepository.save(chatMessage);

        log.info("[WebSocket] 메시지 저장 - chatRoomId: {}, senderId: {}", chatRoomId, senderId);

        // 채팅방 구독자들에게 메시지 브로드캐스트
        // /sub/chat/rooms/{chatRoomId} 구독 중인 클라이언트에게 전송
        messagingTemplate.convertAndSend(
                "/sub/chat/rooms/" + chatRoomId,
                new ChatMessageResponseDto(
                        chatMessage.getId(),
                        chatRoomId,
                        senderId,
                        userService.getUser(senderId).nickname(), // 발신자 닉네임 조회
                        chatMessage.getContent(),
                        chatMessage.isRead(),
                        chatMessage.getCreatedAt()
                )
        );
    }
}