package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
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

import java.time.LocalDateTime;
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
        // (신청자 취소 후 재신청 시에도 기존 채팅방 재사용)
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
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // DEACTIVATED 채팅방은 조회 불가 (매칭 취소)
        // READ_ONLY는 조회 가능 — 별도 체크 없이 통과
        if (chatRoom.isDeactivated()) {
            throw new ChatException(ErrorCode.CHAT_ROOM_DEACTIVATED);
        }

        // 채팅방 참여자 여부 확인 + joinedAt 조회
        ChatMember chatMember = chatMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_NOT_PARTICIPANT));

        // 참여자 입장 시각 (이 시각 이후 메시지만 반환)
        LocalDateTime joinedAt = chatMember.getCreatedAt();

        // 입장 시각 이후 메시지만 조회 (이전 참여자와의 대화 격리)
        List<ChatMessage> messages = chatMessageRepository
                .findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
                        chatRoomId, cursorId, joinedAt, PageRequest.of(0, size + 1));


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

    // ChatServiceImpl 구현
    @Transactional
    @Override
    public void removeChatMember(Long postId, Long userId) {
        // postId로 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        // 해당 유저를 ChatMember에서 제거
        chatMemberRepository.deleteByChatRoomIdAndUserId(chatRoom.getId(), userId);
    }

    // 채팅방 참여자 목록 조회 - 채팅방 멤버만 접근 가능
    @Override
    public List<ChatMemberResponseDto> getChatMembers(Long chatRoomId, Long userId) {

        // 채팅방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // DEACTIVATED 채팅방은 접근 불가 (매칭 취소)
        if (chatRoom.isDeactivated()) {
            throw new ChatException(ErrorCode.CHAT_ROOM_DEACTIVATED);
        }

        // 채팅방 참여자 여부 확인
        if (!chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new ChatException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }

        // 참여자 목록 조회
        List<ChatMember> members = chatMemberRepository.findByChatRoomId(chatRoomId);

        // 유저 ID 목록 한 번에 조회 (N+1 방지)
        List<Long> userIds = members.stream()
                .map(ChatMember::getUserId)
                .toList();
        Map<Long, UserInfoDto> userInfoMap = userService.getUserInfos(userIds);

        // DTO 변환
        return members.stream()
                .map(m -> ChatMemberResponseDto.of(
                        m,
                        userInfoMap.containsKey(m.getUserId())
                                ? userInfoMap.get(m.getUserId()).nickname()
                                : null
                ))
                .toList();
    }
}