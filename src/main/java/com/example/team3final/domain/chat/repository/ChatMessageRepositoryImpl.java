package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.QChatMessage;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    // QueryDSL 쿼리 생성을 담당하는 객체
    private final JPAQueryFactory queryFactory;

    // QueryDSL Q클래스
    private static final QChatMessage chatMessage = QChatMessage.chatMessage;

    // 채팅방 메시지 조회 - 기본 커서 조회
    // 기존 ChatMessageRepository의 메서드명과 반환 타입을 유지
    // 서비스 계층 코드를 수정하지 않기 위해 외부 시그니처는 그대로 두고,
    // 실제 QueryDSL 조회 로직은 searchMessages() 공통 메서드에 위임
    @Override
    public List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            Pageable pageable
    ) {
        return searchMessages(chatRoomId, cursorId, null, null, pageable);
    }

    // 채팅방 메시지 조회 - 입장 시간 이후 메시지만 조회
    // joinedAt 이후의 메시지만 보여줘야 하는 경우 사용
    // 기존 JPQL의 m.createdAt >= :joinedAt 조건과 같은 역할
    @Override
    public List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            Pageable pageable
    ) {
        return searchMessages(chatRoomId, cursorId, joinedAt, null, pageable);
    }

    // 채팅방 메시지 조회 - 입장 시간 이후, 퇴장 시간 이전 메시지만 조회
    // joinedAt 이후부터 leftAt 이전까지의 메시지만 보여줘야 하는 경우 사용
    // 기존 JPQL의 m.createdAt >= :joinedAt AND m.createdAt < :leftAt 조건과 같은 역할
    @Override
    public List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtBetweenOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            LocalDateTime leftAt,
            Pageable pageable
    ) {
        return searchMessages(chatRoomId, cursorId, joinedAt, leftAt, pageable);
    }

    /* 채팅 메시지 커서 조회 공통 QueryDSL 메서드
     기존에는 joinedAt, leftAt 조건 유무에 따라 JPQL 메서드가 3개로 나뉘어 있었는데,
     실제 조회 조건은 대부분 같기 때문에 공통 메서드 하나로 합침 */
    /* 공통 조건
     -> chatRoomId가 일치하는 메시지
     -> cursorId보다 작은 id를 가진 메시지
     -> id 내림차순 정렬
     -> Pageable의 offset, limit 적용
     선택 조건:
     -> joinedAt이 있으면 createdAt >= joinedAt 조건 적용
     -> leftAt이 있으면 createdAt < leftAt 조건 적용 */
    private List<ChatMessage> searchMessages(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            LocalDateTime leftAt,
            Pageable pageable
    ) {
        // 동적 조건을 하나의 Predicate로 조립,
        // joinedAt 또는 leftAt이 null이면 해당 조건은 buildPredicate() 내부에서 제외
        Predicate predicate = buildPredicate(
                hasChatRoomId(chatRoomId),
                beforeCursor(cursorId),
                afterJoinedAt(joinedAt),
                beforeLeftAt(leftAt)
        );

        // 실제 메시지 조회 쿼리
        // 기존 JPQL과 동일하게 id DESC 정렬을 유지
        return queryFactory
                .selectFrom(chatMessage)
                .where(predicate)
                .orderBy(chatMessage.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    // 여러 BooleanExpression 조건을 하나의 Predicate로 묶어줌
    // 각 조건 메서드는 조건이 필요 없을 때 null을 반환,
    // BooleanBuilder는 null이 아닌 조건만 and 조건으로 연결
    private Predicate buildPredicate(BooleanExpression... expressions) {
        BooleanBuilder builder = new BooleanBuilder();

        for (BooleanExpression expression : expressions) {
            if (expression != null) {
                builder.and(expression);
            }
        }

        return builder;
    }

    // 채팅방 ID 조건
    // chatRoomId가 존재하면 m.chatRoomId = :chatRoomId 조건을 적용
    private BooleanExpression hasChatRoomId(Long chatRoomId) {
        return chatRoomId != null ? chatMessage.chatRoomId.eq(chatRoomId) : null;
    }

    // 커서 조건
    // cursorId가 존재하면 m.id < :cursorId 조건을 적용
    // 최신순 커서 기반 조회에서 이미 조회한 메시지보다 이전 메시지를 가져오기 위한 조건
    private BooleanExpression beforeCursor(Long cursorId) {
        return cursorId != null ? chatMessage.id.lt(cursorId) : null;
    }

    // 입장 시간 이후 조건
    // joinedAt이 존재하면 m.createdAt >= :joinedAt 조건을 적용
    // 채팅방 입장 이전 메시지를 숨기기 위해 사용
    private BooleanExpression afterJoinedAt(LocalDateTime joinedAt) {
        return joinedAt != null ? chatMessage.createdAt.goe(joinedAt) : null;
    }

    // 퇴장 시간 이전 조건
    // leftAt이 존재하면 m.createdAt < :leftAt 조건을 적용
    // 퇴장 이후 메시지를 숨기기 위해 사용
    private BooleanExpression beforeLeftAt(LocalDateTime leftAt) {
        return leftAt != null ? chatMessage.createdAt.lt(leftAt) : null;
    }
}
