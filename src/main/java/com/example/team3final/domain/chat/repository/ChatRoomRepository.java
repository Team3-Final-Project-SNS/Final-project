package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 게시글 ID로 채팅방 조회 - 채팅방 존재 여부 확인에 사용
    Optional<ChatRoom> findByPostId(Long postId);

    // READ_ONLY 상태이면서 deactivatedAt이 지난 채팅방 조회
    // → 만남 완료 후 2시간 경과한 채팅방 (노쇼 방은 deactivatedAt = null 이라 제외됨)
    List<ChatRoom> findByStatusAndDeactivatedAtBefore(ChatRoomStatus status, LocalDateTime now);

    // 매칭 목록 조회(getMatches)에서 채팅방 N+1을 막기 위한 메서드
    List<ChatRoom> findByPostIdIn(List<Long> postIds);
}