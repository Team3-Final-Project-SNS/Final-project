package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            throw new ChatException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
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
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.deactivateNow();
    }

    // 채팅방 2시간 후 비활성화 예약 - 만남 인증 완료 시 내부 호출
    @Transactional
    @Override
    public void scheduleChatRoomDeactivation(Long postId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.scheduleDeactivation();
    }

    // 메시지 목록 조회 (커서 기반 페이징)
    @Transactional
    @Override
    public CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size) {

        // size 최대 제한
        if (size > 50) {
            throw new ChatException(ErrorCode.CHAT_INVALID_PAGE_SIZE);
        }

        // cursorId 유효성
        if (cursorId <= 0) {
            throw new ChatException(ErrorCode.CHAT_INVALID_CURSOR);
        }

        // 채팅방 존재 여부 확인
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 채팅방 참여자 여부 확인
        if (!chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new ChatException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }

        // 메시지 조회 (size+1개 조회로 다음 페이지 여부 확인)
        List<ChatMessage> messages = chatMessageRepository
                .findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, cursorId, PageRequest.of(0, size + 1));

        // 읽음 처리 - 내가 보낸 메시지가 아닌 것만
        messages.stream()
                .filter(m -> !m.getSenderId().equals(userId))
                .filter(m -> !m.isRead())
                .forEach(ChatMessage::markAsRead);

        // 발신자 ID 목록 한 번에 조회 (N+1 방지)
        List<Long> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .distinct()
                .toList();
        Map<Long, UserInfoDto> userInfoMap = userService.getUserInfos(senderIds);

        // DTO 변환
        List<ChatMessageResponseDto> content = messages.stream()
                .map(m -> new ChatMessageResponseDto(
                        m.getId(),
                        chatRoomId,
                        m.getSenderId(),
                        userInfoMap.containsKey(m.getSenderId())
                                ? userInfoMap.get(m.getSenderId()).nickname()
                                : null,
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

    // ChatServiceImpl 에 구현
    @Override
    public Map<Long, Long> getChatRoomIdsByPostIds(List<Long> postIds) {
        // 빈 리스트 가드 — IN 절 빈 컬렉션 방지
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // post_id IN (...) 단일 쿼리로 채팅방 일괄 조회
        List<ChatRoom> rooms = chatRoomRepository.findByPostIdIn(postIds);
        // Map<postId, chatRoomId> 변환
        return rooms.stream()
                .collect(Collectors.toMap(
                        ChatRoom::getPostId,   // Key — ChatRoom 에 getPostId() 있음(확인됨)
                        ChatRoom::getId        // Value — 채팅방 ID
                ));
    }

    // 이의 제기 상세 조회 -> 참여자 검증/읽음 처리 없이 전체 메시지 조회
    @Override
    public List<ChatMessageResponseDto> getChatMessagesForAdmin(Long chatRoomId) {

        // 채팅방 존재 여부 확인
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 전체 메시지 오래된 순으로 조회 (대화 흐름 파악용)
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByIdAsc(chatRoomId);

        // 발신자 ID 목록 한 번에 조회
        List<Long> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .distinct()
                .toList();

        Map<Long, UserInfoDto> userInfoDtoMap = userService.getUserInfos(senderIds);

        // DTO 변환
        return messages.stream()
                .map(m -> new ChatMessageResponseDto(
                        m.getId(),
                        chatRoomId,
                        m.getSenderId(),
                        userInfoDtoMap.containsKey(m.getSenderId())
                        ? userInfoDtoMap.get(m.getSenderId()).nickname() : null,
                        m.getContent(),
                        m.isRead(),
                        m.getCreatedAt()
                ))
                .toList();
    }
}