package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepositoryCustom {

    // 채팅방 메시지 조회 - 기본 커서 조회
    // 기존 ChatMessageRepository의 메서드명과 반환 타입을 유지
    // 서비스 계층 코드를 수정하지 않기 위해 외부 시그니처는 그대로 두고,
    // 실제 QueryDSL 조회 로직은 searchMessages() 공통 메서드에 위임
    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            Pageable pageable
    );

    // 채팅방 메시지 조회 - 입장 시간 이후 메시지만 조회
    // joinedAt 이후의 메시지만 보여줘야 하는 경우 사용
    // 기존 JPQL의 m.createdAt >= :joinedAt 조건과 같은 역할
    List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            Pageable pageable
    );

    // 채팅방 메시지 조회 - 입장 시간 이후, 퇴장 시간 이전 메시지만 조회
    // joinedAt 이후부터 leftAt 이전까지의 메시지만 보여줘야 하는 경우 사용
    // 기존 JPQL의 m.createdAt >= :joinedAt AND m.createdAt < :leftAt 조건과 같은 역할
    List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtBetweenOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            LocalDateTime leftAt,
            Pageable pageable
    );
}
