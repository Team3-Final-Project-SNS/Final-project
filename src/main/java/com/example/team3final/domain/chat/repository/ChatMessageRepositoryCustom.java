package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepositoryCustom {

    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            Pageable pageable
    );

    List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtAfterOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            Pageable pageable
    );

    List<ChatMessage> findByChatRoomIdAndIdLessThanAndCreatedAtBetweenOrderByIdDesc(
            Long chatRoomId,
            Long cursorId,
            LocalDateTime joinedAt,
            LocalDateTime leftAt,
            Pageable pageable
    );
}
