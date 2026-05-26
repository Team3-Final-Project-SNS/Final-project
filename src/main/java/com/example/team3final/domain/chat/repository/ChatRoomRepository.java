package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 게시글 ID로 채팅방 조회 - 채팅방 존재 여부 확인에 사용
    Optional<ChatRoom> findByPostId(Long postId);

    // 활성 상태이면서 비활성화 예정 시각이 지난 채팅방 조회 (스케줄러용)
    List<ChatRoom> findByIsActiveTrueAndDeactivatedAtBefore(LocalDateTime now);
}