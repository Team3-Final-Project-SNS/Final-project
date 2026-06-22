package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ChatException;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatQueryService 단위 테스트")
class ChatQueryServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserInternalService userInternalService;

    @InjectMocks
    private ChatQueryServiceImpl chatQueryService;

    @Test
    @DisplayName("채팅 메시지 조회는 참여자 검증 후 메시지를 반환하고 상대 메시지를 읽음 처리한다")
    void getChatMessages_shouldReturnMessagesAndMarkOpponentMessageRead() {
        ChatRoom chatRoom = ChatRoom.builder().postId(10L).roomType(ChatRoomType.ONE_TO_ONE).build();
        ChatMember chatMember = ChatMember.builder().chatRoomId(1L).userId(1L).role(ChatMemberRole.HOST).build();
        ReflectionTestUtils.setField(chatMember, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        ChatMessage message = ChatMessage.builder().chatRoomId(1L).senderId(2L).content("안녕하세요").build();
        ReflectionTestUtils.setField(message, "id", 100L);
        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(chatRoom));
        when(chatMemberRepository.findByChatRoomIdAndUserId(1L, 1L)).thenReturn(Optional.of(chatMember));
        when(chatMessageRepository.findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
                eq(1L), eq(Long.MAX_VALUE), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(userInternalService.getUserInfos(List.of(2L)))
                .thenReturn(Map.of(2L, new UserInfoDto(2L, "상대", "컴퓨터공학", "20", new BigDecimal("36.5"), 1L)));

        CursorResponseDto<ChatMessageResponseDto> result =
                chatQueryService.getChatMessages(1L, 1L, Long.MAX_VALUE, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).senderNickname()).isEqualTo("상대");
        assertThat(message.isRead()).isTrue();
    }

    @Test
    @DisplayName("채팅 메시지 조회는 잘못된 커서이면 채팅 예외를 던진다")
    void getChatMessages_shouldThrowWhenCursorInvalid() {
        assertThatThrownBy(() -> chatQueryService.getChatMessages(1L, 1L, 0L, 10))
                .isInstanceOf(ChatException.class);
    }

    @Test
    @DisplayName("채팅방 참여자 조회는 현재 참여 중인 멤버 목록을 반환한다")
    void getChatMembers_shouldReturnActiveMembers() {
        ChatRoom chatRoom = ChatRoom.builder().postId(10L).roomType(ChatRoomType.ONE_TO_ONE).build();
        ChatMember requester = ChatMember.builder().chatRoomId(1L).userId(1L).role(ChatMemberRole.HOST).build();
        ChatMember opponent = ChatMember.builder().chatRoomId(1L).userId(2L).role(ChatMemberRole.GUEST).build();
        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(chatRoom));
        when(chatMemberRepository.findByChatRoomIdAndUserId(1L, 1L)).thenReturn(Optional.of(requester));
        when(chatMemberRepository.findByChatRoomIdAndLeftAtIsNull(1L)).thenReturn(List.of(requester, opponent));
        when(userInternalService.getUserInfos(List.of(1L, 2L))).thenReturn(Map.of(
                1L, new UserInfoDto(1L, "나", "컴퓨터공학", "20", new BigDecimal("36.5"), 1L),
                2L, new UserInfoDto(2L, "상대", "컴퓨터공학", "20", new BigDecimal("36.5"), 1L)));

        List<ChatMemberResponseDto> result = chatQueryService.getChatMembers(1L, 1L);

        assertThat(result).hasSize(2);
        verify(chatMemberRepository).findByChatRoomIdAndLeftAtIsNull(1L);
    }
}
