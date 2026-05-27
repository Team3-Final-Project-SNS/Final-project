package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 채팅방 ID로 메세지 목록 조회 - 최신순 페이징
    // cursorId 이전 메세지만 조회 (커서 기반 페이징)
    @Query("SELECT m FROM ChatMessage m WHERE  m.chatRoomId = :chatRoomId AND m.id < :cursorId ORDER BY m.id DESC")
    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            @Param("chatRoomId") Long chatRoomId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 읽지 않은 메세지 수 조회 - 채팅방 목록에서 안읽은 메세지 카운트
    long countByChatRoomIdAndIsReadFalseAndSenderIdNot(Long chatRoomId, Long userId);

    // 채팅방의 마지막 메시지 조회 - 채팅방 목록에서 마지막 메시지 표시용
    Optional<ChatMessage> findTopByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    // 채팅방 전체 메시지 오래된 순 조회 — 어드민 이의제기 상세 조회용
    List<ChatMessage> findByChatRoomIdOrderByIdAsc(Long chatRoomId);
}
