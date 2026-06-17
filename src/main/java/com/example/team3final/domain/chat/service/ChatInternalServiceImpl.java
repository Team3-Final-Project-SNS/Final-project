package com.example.team3final.domain.chat.service;

import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatMemberStatus;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.chat.util.ChatRedisZSetKeys;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Chat 도메인의 타 도메인 호출용 채팅방 생성/상태 변경/관리자 조회 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatInternalServiceImpl implements ChatInternalService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserInternalService userInternalService;
    private final StringRedisTemplate redisTemplate;

    // 한국 시간대 오프셋 — Unix Timestamp 변환 시 KST(UTC+9) 기준 적용
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    @Override
    @Transactional
    public Long createChatRoom(Long postId, Long authorId, Long applicantId, int maxApplicants) {

        Optional<ChatRoom> existing = chatRoomRepository.findByPostId(postId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // 채팅방 생성
        try {
            // 게시글 정원 기준 2인은 1:1, 3인 이상은 그룹 채팅방 생성
            ChatRoomType roomType = maxApplicants > 2 ? ChatRoomType.GROUP : ChatRoomType.ONE_TO_ONE;
            ChatRoom chatRoom = ChatRoom.builder()
                    .postId(postId)
                    .roomType(roomType)
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
        } catch (DataIntegrityViolationException e) {
            return chatRoomRepository.findByPostId(postId)
                    .map(ChatRoom::getId)
                    .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        }
    }

    // 채팅방 즉시 비활성화 - 취소/노쇼 시 내부 호출
    @Override
    @Transactional
    public void deactivateChatRoom(Long postId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoom.deactivateNow();

        // 즉시 비활성화 시 ZSet 예약 제거 (예약된 게 있을 수 있음)
        redisTemplate.opsForZSet().remove(
                ChatRedisZSetKeys.ROOM_DEACTIVATE,
                String.valueOf(chatRoom.getId())
        );
    }

    // 채팅방 2시간 후 비활성화 예약 - 만남 인증 완료 시 내부 호출
    @Override
    @Transactional
    public void scheduleChatRoomDeactivation(Long postId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        // ACTIVE 유지, deactivatedAt만 세팅 (스케줄러가 READ_ONLY로 전환)
        chatRoom.scheduleDeactivation();

        // 채팅방 READ_ONLY 전환 ZSet 예약
        // score = deactivatedAt Unix Timestamp (2시간 후)
        // member = chatRoomId
        redisTemplate.opsForZSet().add(
                ChatRedisZSetKeys.ROOM_DEACTIVATE,
                String.valueOf(chatRoom.getId()),
                chatRoom.getDeactivatedAt().toEpochSecond(KST)
        );
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

        Map<Long, UserInfoDto> userInfoDtoMap = userInternalService.getUserInfos(senderIds);

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

    // 신청자 취소 시 ChatMember를 삭제하지 않고 퇴장 시각만 기록
    // 삭제하면 leftAt 이전 채팅 기록을 조회할 수 없으므로,
    // LEFT 상태 + leftAt 기록 방식으로 변경 (이전 대화 이력 보존)
    @Override
    @Transactional
    public void removeChatMember(Long postId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        // LEFT 상태로 변경 + leftAt = 현재 시각 기록
        chatMemberRepository.updateStatusAndLeftAt(
                chatRoom.getId(),
                userId,
                ChatMemberStatus.LEFT,
                LocalDateTime.now()
        );
    }

    // 채팅방 존재 여부 확인 - 첫 신청 여부 판단용
    @Override
    public boolean existsChatRoomByPostId(Long postId) {
        // postId로 채팅방이 있는지만 확인
        // 없으면 첫 신청 → 채팅방 생성 필요
        return chatRoomRepository.findByPostId(postId).isPresent();
    }

    // 그룹 채팅방에 신청자 멤버 추가 - 두 번째 이후 신청자용
    @Override
    @Transactional
    public void addChatMember(Long postId, Long applicantId) {
        // postId로 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 이미 멤버면 중복 추가 방지 (멱등성 보장)
        if (chatMemberRepository.existsByChatRoomIdAndUserId(chatRoom.getId(), applicantId)) {
            return;
        }

        // GUEST로 채팅방 멤버 추가
        chatMemberRepository.save(
                ChatMember.builder()
                        .chatRoomId(chatRoom.getId())
                        .userId(applicantId)
                        .role(ChatMemberRole.GUEST)
                        .build()
        );
    }

    // HOST/BOTH 노쇼 시 채팅방 전체 READ_ONLY
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void makeReadOnlyChatRoom(Long postId) {
        // postId로 채팅방 조회, 없으면 데이터 정합성 오류로 예외 발생
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 이미 READ_ONLY 또는 DEACTIVATED면 스킵 (멱등성 보장)
        if (!chatRoom.isActive()) {
            return;
        }

        // ACTIVE → READ_ONLY 전환
        chatRoom.deactivateByNoShow();
    }

    // GUEST 노쇼 → 해당 멤버만 NO_SHOW / HOST,BOTH 노쇼 → 전체 READ_ONLY
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markGuestNoShow(Long postId, Long applicantId) {
        // postId로 채팅방 조회, 없으면 데이터 정합성 오류로 예외 발생
        ChatRoom chatRoom = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 해당 신청자 멤버 조회, 없으면 데이터 정합성 오류로 예외 발생
        ChatMember chatMember = chatMemberRepository
                .findByChatRoomIdAndUserId(chatRoom.getId(), applicantId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_NOT_PARTICIPANT));

        // 이미 NO_SHOW면 스킵 (멱등성 보장)
        if (chatMember.isNoShow()) {
            return;
        }

        // NO_SHOW 상태 + leftAt 기록 — leftAt 이후 메시지는 조회 불가
        chatMemberRepository.updateStatusAndLeftAt(
                chatRoom.getId(),
                applicantId,
                ChatMemberStatus.NO_SHOW,
                LocalDateTime.now()
        );
    }
}
