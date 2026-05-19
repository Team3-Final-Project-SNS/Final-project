package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 매칭 ID로 채팅방 조회 - 매칭 확정 시 채팅방 존재 여부 확인에 사용
    Optional<ChatRoom> findByMatchId(Long matchId);

    // 내가 등록자이고 나가지 않은 채팅방 목록
    List<ChatRoom> findByAuthorIdAndAuthorLeftFalse(Long authorId);

    // 내가 신청자이고 나가지 않은 채팅방 목록
    List<ChatRoom> findByApplicantIdAndApplicantLeftFalse(Long applicantId);
}
