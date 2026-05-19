package com.example.team3final.domain.chat.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
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
}