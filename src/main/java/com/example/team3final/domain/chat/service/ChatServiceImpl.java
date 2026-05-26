package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserService userService;

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    @Transactional
    @Override
    public Long createChatRoom(Long postId, Long authorId, Long applicantId) {

        // 이미 채팅방이 있으면 생성 안 함
        if (chatRoomRepository.findByPostId(postId).isPresent()) {
            throw new ServiceException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        // 채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .postId(postId)
                .build();
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        // 참여자 등록 (HOST: 등록자, GUEST: 신청자)
        chatMemberRepository.save(
                ChatMember.builder()
                        .chatRoomId(savedChatRoom.getId())
                        .userId(authorId)
                        .role(ChatMemberRole.HOST)
                        .build()
        );
        chatMemberRepository.save(
                ChatMember.builder()
                        .chatRoomId(savedChatRoom.getId())
                        .userId(applicantId)
                        .role(ChatMemberRole.GUEST)
                        .build()
        );

        // TODO: 고도화 시 카프카로 교체 예정 → 해당 라인 삭제될 예정
        return savedChatRoom.getId();
    }

    // 채팅방 즉시 비활성화 - 취소/노쇼 시 내부 호출
    @Transactional
    @Override
    public void deactivateChatRoom(Long postId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.deactivateNow();
    }

    // 채팅방 2시간 후 비활성화 예약 - 만남 인증 완료 시 내부 호출
    @Transactional
    @Override
    public void scheduleChatRoomDeactivation(Long postId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.scheduleDeactivation();
    }

    // 메시지 목록 조회 (커서 기반 페이징)
    @Transactional
    @Override
    public CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size) {

        // 채팅방 존재 여부 확인
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 채팅방 참여자 여부 확인
        if (!chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new ServiceException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }

        // 메시지 조회 (size+1개 조회로 다음 페이지 여부 확인)
        List<ChatMessage> messages = chatMessageRepository
                .findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, cursorId, PageRequest.of(0, size + 1));

        // 읽음 처리 - 내가 보낸 메시지가 아닌 것만
        messages.stream()
                .filter(m -> !m.getSenderId().equals(userId))
                .filter(m -> !m.isRead())
                .forEach(ChatMessage::markAsRead);

        // DTO 변환
        List<ChatMessageResponseDto> content = messages.stream()
                .map(m -> new ChatMessageResponseDto(
                        m.getId(),
                        chatRoomId,
                        m.getSenderId(),
                        userService.getUser(m.getSenderId()).nickname(),
                        m.getContent(),
                        m.isRead(),
                        m.getCreatedAt()
                ))
                .toList();

        return CursorResponseDto.of(content, size, ChatMessageResponseDto::messageId);
    }

    // postId로 chatRoomId 조회 - 매칭 상세 조회에서 사용
    @Override
    public Long getChatRoomIdByPostId(Long postId) {
        return chatRoomRepository.findByPostId(postId)
                .map(ChatRoom::getId)
                .orElse(null);
    }
}