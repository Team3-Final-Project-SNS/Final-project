package com.example.team3final.domain.chat;

import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("채팅방 중복 생성 방지 테스트")
class ChatRoomDuplicateTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Long TEST_POST_ID = 6001L;

    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            chatRoomRepository.deleteAll(
                    chatRoomRepository.findAll().stream()
                            .filter(r -> r.getPostId().equals(TEST_POST_ID)
                                    || r.getPostId().equals(TEST_POST_ID + 1))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    // ====================================================================
    // 테스트 1: 정상 케이스 — 첫 번째 채팅방 생성 성공
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("첫 번째 채팅방 생성 → 정상 저장되어야 한다")
    void firstChatRoom_shouldSaveSuccessfully() {
        // given
        ChatRoom chatRoom = buildChatRoom(TEST_POST_ID);

        // when & then
        assertThatCode(() -> chatRoomRepository.saveAndFlush(chatRoom))
                .doesNotThrowAnyException();

        // 저장 확인
        assertThat(chatRoomRepository.findByPostId(TEST_POST_ID)).isPresent();
    }

    // ====================================================================
    // 테스트 2: 핵심 케이스 — 같은 post_id 로 채팅방 중복 생성 차단
    // ====================================================================
    // uq_chat_rooms_post_id UNIQUE 제약 위반 확인
    // 매칭 확정 시 동시 요청이 들어와도 채팅방은 하나만 생성됨을 보장
    // ====================================================================

    @Test
    @Order(2)
    @DisplayName("같은 post_id 로 채팅방을 2번 생성하면 UNIQUE 제약으로 차단되어야 한다")
    void duplicateChatRoom_shouldBeBlockedByUniqueConstraint() {
        // given — 첫 번째 채팅방 저장
        chatRoomRepository.saveAndFlush(buildChatRoom(TEST_POST_ID));

        // given — 같은 post_id 로 두 번째 채팅방 생성 시도
        ChatRoom duplicate = buildChatRoom(TEST_POST_ID);

        // when & then — UNIQUE 제약 위반 → DataIntegrityViolationException
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_chat_rooms_post_id");
    }

    // ====================================================================
    // 테스트 3: 다른 게시글은 각자 채팅방 생성 가능
    // ====================================================================
    // post_id 단독 UNIQUE 이므로 다른 게시글은 독립적으로 채팅방 생성 가능
    // ====================================================================

    @Test
    @Order(3)
    @DisplayName("다른 post_id 는 각각 채팅방을 생성할 수 있어야 한다")
    void differentPosts_shouldEachHaveChatRoom() {
        // given
        ChatRoom room1 = buildChatRoom(TEST_POST_ID);
        ChatRoom room2 = buildChatRoom(TEST_POST_ID + 1);  // 다른 게시글

        // when & then — 둘 다 저장 성공
        assertThatCode(() -> chatRoomRepository.saveAndFlush(room1))
                .doesNotThrowAnyException();
        assertThatCode(() -> chatRoomRepository.saveAndFlush(room2))
                .doesNotThrowAnyException();

        // 두 채팅방 모두 존재하는지 확인
        assertThat(chatRoomRepository.findByPostId(TEST_POST_ID)).isPresent();
        assertThat(chatRoomRepository.findByPostId(TEST_POST_ID + 1)).isPresent();
    }

    // ====================================================================
    // 테스트 4: 매칭 확정 동시 요청 시나리오 — 버튼 두 번 클릭 재현
    // ====================================================================
    // 매칭 확정 요청이 네트워크 재시도로 두 번 들어오는 상황 재현
    // 채팅방은 정확히 1개만 생성되어야 함
    // ====================================================================

    @Test
    @Order(4)
    @DisplayName("매칭 확정 동시 요청 시나리오 → 채팅방은 정확히 1개만 생성되어야 한다")
    void doubleMatchConfirm_onlyOneChatRoomShouldBeCreated() {
        // given — 동시에 들어온 두 매칭 확정 요청
        ChatRoom firstRequest  = buildChatRoom(TEST_POST_ID);
        ChatRoom secondRequest = buildChatRoom(TEST_POST_ID);

        // when
        chatRoomRepository.saveAndFlush(firstRequest);   // 첫 번째 요청 → 성공

        // then — 두 번째 요청은 UNIQUE 제약으로 차단
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(secondRequest))
                .isInstanceOf(DataIntegrityViolationException.class);

        // DB에 정확히 1개만 존재하는지 확인
        long count = chatRoomRepository.findAll().stream()
                .filter(r -> r.getPostId().equals(TEST_POST_ID))
                .count();

        assertThat(count)
                .as("채팅방은 게시글당 정확히 1개만 존재해야 한다")
                .isEqualTo(1L);
    }

    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    private ChatRoom buildChatRoom(Long postId) {
        return ChatRoom.builder()
                .postId(postId)
                .roomType(ChatRoomType.ONE_TO_ONE)
                .build();
    }
}
