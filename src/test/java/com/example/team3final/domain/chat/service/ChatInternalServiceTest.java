package com.example.team3final.domain.chat.service;

import com.example.team3final.common.exception.ChatException;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatInternalService 서비스 단위 테스트")
class ChatInternalServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private ChatInternalServiceImpl chatInternalService;

    @Test
    @DisplayName("이미 게시글 채팅방이 있으면 새로 생성하지 않고 기존 채팅방 ID를 반환한다")
    void createChatRoom_shouldReturnExistingRoomId() {
        ChatRoom chatRoom = chatRoom(10L, 20L);
        when(chatRoomRepository.findByPostId(20L)).thenReturn(Optional.of(chatRoom));

        Long response = chatInternalService.createChatRoom(20L, 1L, 2L, 2);

        assertThat(response).isEqualTo(10L);
    }

    @Test
    @DisplayName("게시글 ID 목록으로 채팅방 ID 맵을 조회한다")
    void getChatRoomIdsByPostIds_shouldReturnRoomIdMap() {
        ChatRoom chatRoom = chatRoom(10L, 20L);
        when(chatRoomRepository.findByPostIdIn(List.of(20L))).thenReturn(List.of(chatRoom));

        Map<Long, Long> response = chatInternalService.getChatRoomIdsByPostIds(List.of(20L));

        assertThat(response).containsEntry(20L, 10L);
    }

    @Test
    @DisplayName("채팅방 비활성화 시 채팅방이 없으면 실패한다")
    void deactivateChatRoom_shouldThrowWhenRoomNotFound() {
        when(chatRoomRepository.findByPostId(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatInternalService.deactivateChatRoom(20L))
                .isInstanceOf(ChatException.class);
    }

    @Test
    @DisplayName("채팅방 비활성화 예약 시 Redis 예약 정보를 저장한다")
    void scheduleChatRoomDeactivation_shouldAddRedisReservation() {
        ChatRoom chatRoom = chatRoom(10L, 20L);
        when(chatRoomRepository.findByPostId(20L)).thenReturn(Optional.of(chatRoom));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        chatInternalService.scheduleChatRoomDeactivation(20L);

        assertThat(chatRoom.getDeactivatedAt()).isNotNull();
        verify(zSetOperations).add(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("10"), org.mockito.ArgumentMatchers.anyDouble());
    }

    private ChatRoom chatRoom(Long id, Long postId) {
        ChatRoom chatRoom = ChatRoom.builder()
                .postId(postId)
                .roomType(ChatRoomType.ONE_TO_ONE)
                .build();
        ReflectionTestUtils.setField(chatRoom, "id", id);
        return chatRoom;
    }
}
