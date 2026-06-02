package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // joinedAt 필터 적용 - 입장 시각 이후 메시지만 조회
    // 그룹 채팅에서 나중에 입장한 참여자는 입장 전 대화 격리
    // joinedAt: 참여자 입장 시각 (ChatMember.createdAt)
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoomId = :chatRoomId AND m.id < :cursorId AND m.createdAt >= :joinedAt ORDER BY m.id DESC")
    List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
            @Param("chatRoomId") Long chatRoomId,
            @Param("cursorId") Long cursorId,
            @Param("joinedAt") LocalDateTime joinedAt,
            Pageable pageable
    );

    // 채팅방 전체 메시지 오래된 순 조회 — 어드민 이의제기 상세 조회용
    List<ChatMessage> findByChatRoomIdOrderByIdAsc(Long chatRoomId);
}
