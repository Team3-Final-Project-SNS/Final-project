package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberStatus;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Chat 도메인의 채팅 메시지/참여자 조회 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatQueryServiceImpl implements ChatQueryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserInternalService userInternalService;

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
        if (chatMember.getStatus() == ChatMemberStatus.LEFT) {
            throw new ChatException(ErrorCode.CHAT_CANCELLED_PARTICIPANT);
        }

        // 참여자 입장 시각 (이 시각 이후 메시지만 반환)
        LocalDateTime joinedAt = chatMember.getCreatedAt();

        // 노쇼 멤버면 leftAt 이후 메시지 차단 (null이면 정상 참여 중 → 제한 없음)
        LocalDateTime readableUntil = chatMember.getLeftAt();

        // leftAt 여부에 따라 쿼리 분기
        // 노쇼 멤버: leftAt 이전 메시지만 DB에서 조회 (페이지네이션 정확성 보장)
        // 정상 멤버: joinedAt 이후 전체 조회
        List<ChatMessage> messages;
        if (readableUntil != null) {
            // 노쇼 판정 시각 이전 메시지만 조회 — DB 레벨에서 필터링
            messages = chatMessageRepository.findByChatRoomIdAndIdLessThanAndCreatedAtBetweenOrderByIdDesc(
                    chatRoomId, cursorId, joinedAt, readableUntil, PageRequest.of(0, size + 1));
        } else {
            // 정상 참여자 — 입장 시각 이후 전체 조회
            messages = chatMessageRepository.findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
                    chatRoomId, cursorId, joinedAt, PageRequest.of(0, size + 1));
        }

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
        Map<Long, UserInfoDto> userInfoMap = userInternalService.getUserInfos(senderIds);

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
        ChatMember chatMember = chatMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_NOT_PARTICIPANT));
        if (chatMember.getStatus() == ChatMemberStatus.LEFT) {
            throw new ChatException(ErrorCode.CHAT_CANCELLED_PARTICIPANT);
        }

        // leftAt이 null인 현재 참여 중인 멤버만 조회 (취소 퇴장한 멤버 제외)
        List<ChatMember> members = chatMemberRepository.findByChatRoomIdAndLeftAtIsNull(chatRoomId);

        // 유저 ID 목록 한 번에 조회 (N+1 방지)
        List<Long> userIds = members.stream()
                .map(ChatMember::getUserId)
                .toList();
        Map<Long, UserInfoDto> userInfoMap = userInternalService.getUserInfos(userIds);

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
