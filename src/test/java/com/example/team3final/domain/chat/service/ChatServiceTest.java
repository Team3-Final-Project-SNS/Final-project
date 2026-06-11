package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatMemberStatus;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMemberRepository chatMemberRepository;
    @Mock
    private UserService userService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("채팅방 존재 여부 확인 - 성공")
    void existsChatRoomByPostId_Success() {
        // given
        Long postId = 1L;
        given(chatRoomRepository.findByPostId(postId)).willReturn(Optional.empty());

        // when
        boolean exists = chatService.existsChatRoomByPostId(postId);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("채팅방 생성 - 성공")
    void createChatRoom_Success() {
        ChatRoom savedRoom = createChatRoom(10L, 100L);
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(savedRoom);

        Long result = chatService.createChatRoom(100L, 1L, 2L);

        assertThat(result).isEqualTo(10L);
        verify(chatMemberRepository).save(argThat(member -> member.getUserId().equals(1L)));
        verify(chatMemberRepository).save(argThat(member -> member.getUserId().equals(2L)));
    }

    @Test
    @DisplayName("채팅방 즉시 비활성화 - 성공")
    void deactivateChatRoom_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(room));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        chatService.deactivateChatRoom(100L);

        assertThat(room.isDeactivated()).isTrue();
        verify(zSetOperations).remove(anyString(), eq("10"));
    }

    @Test
    @DisplayName("채팅방 비활성화 예약 - 성공")
    void scheduleChatRoomDeactivation_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(room));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        chatService.scheduleChatRoomDeactivation(100L);

        assertThat(room.getDeactivatedAt()).isNotNull();
        verify(zSetOperations).add(anyString(), eq("10"), anyDouble());
    }

    @Test
    @DisplayName("채팅 메시지 조회 - 성공")
    void getChatMessages_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        ChatMember member = createChatMember(1L, 10L, 1L, ChatMemberRole.HOST);
        ChatMessage message = createChatMessage(100L, 10L, 2L);
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
        given(chatMemberRepository.findByChatRoomIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        given(chatMessageRepository.findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
                eq(10L), eq(Long.MAX_VALUE), any(LocalDateTime.class), any(PageRequest.class)))
                .willReturn(List.of(message));
        given(userService.getUserInfos(List.of(2L))).willReturn(Map.of(2L, userInfo(2L)));

        CursorResponseDto<ChatMessageResponseDto> result =
                chatService.getChatMessages(10L, 1L, Long.MAX_VALUE, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(message.isRead()).isTrue();
    }

    @Test
    @DisplayName("게시글 ID로 채팅방 ID 조회 - 성공")
    void getChatRoomIdByPostId_Success() {
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(createChatRoom(10L, 100L)));

        Long result = chatService.getChatRoomIdByPostId(100L);

        assertThat(result).isEqualTo(10L);
    }

    @Test
    @DisplayName("게시글 ID 목록으로 채팅방 ID 맵 조회 - 성공")
    void getChatRoomIdsByPostIds_Success() {
        given(chatRoomRepository.findByPostIdIn(List.of(100L))).willReturn(List.of(createChatRoom(10L, 100L)));

        Map<Long, Long> result = chatService.getChatRoomIdsByPostIds(List.of(100L));

        assertThat(result).containsEntry(100L, 10L);
    }

    @Test
    @DisplayName("관리자 채팅 메시지 조회 - 성공")
    void getChatMessagesForAdmin_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        ChatMessage message = createChatMessage(100L, 10L, 2L);
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
        given(chatMessageRepository.findByChatRoomIdOrderByIdAsc(10L)).willReturn(List.of(message));
        given(userService.getUserInfos(List.of(2L))).willReturn(Map.of(2L, userInfo(2L)));

        List<ChatMessageResponseDto> result = chatService.getChatMessagesForAdmin(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("채팅 멤버 제거 - 성공")
    void removeChatMember_Success() {
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(createChatRoom(10L, 100L)));

        chatService.removeChatMember(100L, 2L);

        verify(chatMemberRepository).updateStatusAndLeftAt(eq(10L), eq(2L), eq(ChatMemberStatus.LEFT), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("채팅 멤버 목록 조회 - 성공")
    void getChatMembers_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        ChatMember member = createChatMember(1L, 10L, 1L, ChatMemberRole.HOST);
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
        given(chatMemberRepository.existsByChatRoomIdAndUserId(10L, 1L)).willReturn(true);
        given(chatMemberRepository.findByChatRoomIdAndLeftAtIsNull(10L)).willReturn(List.of(member));
        given(userService.getUserInfos(List.of(1L))).willReturn(Map.of(1L, userInfo(1L)));

        List<ChatMemberResponseDto> result = chatService.getChatMembers(10L, 1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("채팅 멤버 추가 - 성공")
    void addChatMember_Success() {
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(createChatRoom(10L, 100L)));
        given(chatMemberRepository.existsByChatRoomIdAndUserId(10L, 2L)).willReturn(false);

        chatService.addChatMember(100L, 2L);

        verify(chatMemberRepository).save(argThat(member -> member.getUserId().equals(2L)));
    }

    @Test
    @DisplayName("게스트 노쇼 처리 - 성공")
    void markGuestNoShow_Success() {
        ChatMember member = createChatMember(1L, 10L, 2L, ChatMemberRole.GUEST);
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(createChatRoom(10L, 100L)));
        given(chatMemberRepository.findByChatRoomIdAndUserId(10L, 2L)).willReturn(Optional.of(member));

        chatService.markGuestNoShow(100L, 2L);

        verify(chatMemberRepository).updateStatusAndLeftAt(eq(10L), eq(2L), eq(ChatMemberStatus.NO_SHOW), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("채팅방 읽기 전용 처리 - 성공")
    void makeReadOnlyChatRoom_Success() {
        ChatRoom room = createChatRoom(10L, 100L);
        given(chatRoomRepository.findByPostId(100L)).willReturn(Optional.of(room));

        chatService.makeReadOnlyChatRoom(100L);

        assertThat(room.isReadOnly()).isTrue();
    }

    private ChatRoom createChatRoom(Long id, Long postId) {
        ChatRoom room = ChatRoom.builder()
                .postId(postId)
                .build();
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private ChatMember createChatMember(Long id, Long chatRoomId, Long userId, ChatMemberRole role) {
        ChatMember member = ChatMember.builder()
                .chatRoomId(chatRoomId)
                .userId(userId)
                .role(role)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.now().minusMinutes(10));
        return member;
    }

    private ChatMessage createChatMessage(Long id, Long chatRoomId, Long senderId) {
        ChatMessage message = ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .senderId(senderId)
                .content("hello")
                .build();
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
        return message;
    }

    private UserInfoDto userInfo(Long userId) {
        return new UserInfoDto(userId, "nickname" + userId, "major", "24", new BigDecimal("36.5"), 1L);
    }
}
