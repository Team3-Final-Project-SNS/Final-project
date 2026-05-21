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
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchQueryService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostQueryService;
import com.example.team3final.domain.user.service.UserService;
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
    private final UserService userService;
    private final MatchQueryService matchQueryService;
    private final PostQueryService postQueryService;

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    @Transactional
    @Override
    public Long createChatRoom(Long matchId) {

        // 이미 채팅방이 있으면 생성 안 함
        if (chatRoomRepository.findByMatchId(matchId).isPresent()) {
            throw new ServiceException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        // matchId로 applicantId 조회
        MatchInfoDto matchInfo = matchQueryService.getMatchInfo(matchId);
        Long applicantId = matchInfo.applicantId();

        // postId로 authorId 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());
        Long authorId = postInfo.authorId();

        ChatRoom chatRoom = ChatRoom.builder()
                .matchId(matchId)
                .authorId(authorId)
                .applicantId(applicantId)
                .build();
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        // TODO: 고도화 시 카프카로 교체 예정 → 해당 라인 삭제될 예정
        return savedChatRoom.getId();  // // 생성된 채팅방 ID 반환
    }

    // 채팅방 즉시 비활성화 - 취소/노쇼 시 내부 호출
    @Transactional
    @Override
    public void deactivateChatRoom(Long matchId) {
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.deactivateNow(); // 즉시 비활성화
    }

    // 채팅방 2시간 후 비활성화 예약 - 만남 인증 완료 시 내부 호출
    @Transactional
    @Override
    public void scheduleChatRoomDeactivation(Long matchId) {
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.scheduleDeactivation(); // 2시간 후 비활성화 예약
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

                    Long opponentId = room.getAuthorId().equals(userId) ? room.getApplicantId() : room.getAuthorId();

                    return new ChatRoomResponseDto(
                            room.getId(),                                            // 채팅방 ID
                            room.getMatchId(),                                       // 매칭 ID
                            opponentId,                                              // 상대방 ID
                            userService.getUser(opponentId).nickname(),              // 상대방 닉네임 조회
                            lastMessage != null ? lastMessage.getContent() : null,   // 마지막 메시지
                            lastMessage != null ? lastMessage.getCreatedAt() : null, // 마지막 메시지 시각
                            unreadCount,                                             // 안읽은 메시지 수
                            room.isActive(),                                         // 활성 여부
                            room.getCreatedAt()                                      // 채팅방 생성일
                    );
                })
                .toList();
    }

    // 메시지 목록 조회 (커서 기반 페이징)
    @Override
    public CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size) {
        // 채팅방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 채팅방 참여자 여부 확인
        if (!chatRoom.getAuthorId().equals(userId) && !chatRoom.getApplicantId().equals(userId)) {
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
                        m.getId(),                                         // 메시지 ID
                        chatRoomId,                                        // 채팅방 ID
                        m.getSenderId(),                                   // 발신자 ID
                        userService.getUser(m.getSenderId()).nickname(),   // 발신자 닉네임 조회
                        m.getContent(),                                    // 메시지 내용
                        m.isRead(),                                        // 읽음 여부
                        m.getCreatedAt()                                   // 메시지 생성일
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

    @Override
    public Long getChatRoomIdByMatchId(Long matchId) {
        return chatRoomRepository.findByMatchId(matchId)
                .map(ChatRoom::getId)
                .orElse(null);
    }

}