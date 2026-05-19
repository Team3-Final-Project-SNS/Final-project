package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    // TODO: Match 도메인 구현 완료 후 authorId, applicantId 파라미터 삭제 예정
    @Transactional
    @Override
    public void createChatRoom(Long matchId, Long authorId, Long applicantId) {
        // 이미 채팅방이 있으면 생성 안 함
        if (chatRoomRepository.findByMatchId(matchId).isPresent()) {
            throw new ServiceException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }
        ChatRoom chatRoom = ChatRoom.builder()
                .matchId(matchId)
                .authorId(authorId)       // TODO: Match 도메인 구현 완료 후 삭제 예정
                .applicantId(applicantId) // TODO: Match 도메인 구현 완료 후 삭제 예정
                .build();
        chatRoomRepository.save(chatRoom);
    }

    // 채팅방 비활성화 - 완료/취소/노쇼 시 내부 호출
    @Transactional
    @Override
    public void deactivateChatRoom(Long matchId) {
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.deactivate();
    }

    // 채팅방 목록 조회
    @Override
    public List<ChatRoomResponseDto> getChatRooms(Long userId) {
        // 내가 등록자인 채팅방 + 내가 신청자인 채팅방 합치기
        List<ChatRoom> authorRooms = chatRoomRepository.findByAuthorIdAndAuthorLeftFalse(userId);
        List<ChatRoom> applicantRooms = chatRoomRepository.findByApplicantIdAndApplicantLeftFalse(userId);

        // 두 목록 합치기
        List<ChatRoom> allRooms = new ArrayList<>();
        allRooms.addAll(authorRooms);
        allRooms.addAll(applicantRooms);

        // DTO 변환
        return allRooms.stream()
                .map(room -> {
                    // 마지막 메시지 조회
                    ChatMessage lastMessage = chatMessageRepository
                            .findTopByChatRoomIdOrderByCreatedAtDesc(room.getId())
                            .orElse(null);

                    // 안읽은 메시지 수 조회
                    long unreadCount = chatMessageRepository
                            .countByChatRoomIdAndIsReadFalseAndSenderIdNot(room.getId(), userId);

                    return new ChatRoomResponseDto(
                            room.getId(),
                            room.getMatchId(),
                            // 상대방 ID: 내가 등록자면 신청자ID, 내가 신청자면 등록자ID
                            room.getAuthorId().equals(userId) ? room.getApplicantId() : room.getAuthorId(),
                            null, // TODO: UserService 완성 후 상대방 닉네임 조회
                            lastMessage != null ? lastMessage.getContent() : null,    // 마지막 메시지
                            lastMessage != null ? lastMessage.getCreatedAt() : null,  // 마지막 메시지 시각
                            unreadCount,                                               // 안읽은 메시지 수
                            room.isActive(),
                            room.getCreatedAt()
                    );
                })
                .toList();
    }

    // 메시지 목록 조회 (커서 기반 페이징)
    @Override
    public CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size) {
        // 채팅방 존재 여부 확인
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));

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
                        null, // TODO: UserService 완성 후 닉네임 조회
                        m.getContent(),
                        m.isRead(),
                        m.getCreatedAt()
                ))
                .toList();

        return CursorResponseDto.of(content, size, ChatMessageResponseDto::messageId);
    }

    // 채팅방 나가기 - 완료/취소/노쇼 후에만 가능
    @Transactional
    @Override
    public void leaveChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 활성화된 채팅방은 나가기 불가 (MATCHED 상태)
        // TODO: 매칭 팀원이 완료/취소/노쇼 처리 시 deactivateChatRoom(matchId) 반드시 호출 필요
        // 호출하지 않으면 isActive = true 상태가 유지되어 나가기가 불가능함
        if (chatRoom.isActive()) {
            throw new ServiceException(ErrorCode.CHAT_ROOM_ACTIVE_CANNOT_LEAVE);
        }

        // 등록자/신청자 구분 후 나가기 처리
        if (chatRoom.getAuthorId().equals(userId)) {
            chatRoom.authorLeave();
        } else if (chatRoom.getApplicantId().equals(userId)) {
            chatRoom.applicantLeave();
        } else {
            throw new ServiceException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }
    }

}