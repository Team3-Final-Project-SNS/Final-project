package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    // 특정 채팅방의 모든 멤버 조회
    List<ChatMember> findByChatRoomId(Long chatRoomId);

    // 특정 채팅방에서 특정 유저의 멤버 정보 조회 (참여자 검증)
    Optional<ChatMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    // 특정 채팅방에서 특정 역할의 멤버 조회
    Optional<ChatMember> findByChatRoomIdAndRole(Long chatRoomId, ChatMemberRole role);

    // 특정 채팅방에 해당 유저가 존재하는지 확인
    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    // leftAt이 null인 현재 참여 중인 멤버만 조회
    List<ChatMember> findByChatRoomIdAndLeftAtIsNull(Long chatRoomId);

    // 특정 채팅방에서 특정 유저의 멤버 상태를 NO_SHOW로 변경
    // GUEST 노쇼 판정 시 호출 — 삭제 대신 상태 변경으로 처리
    @Modifying
    @Query("UPDATE ChatMember cm SET cm.status = :status, cm.leftAt = :leftAt WHERE cm.chatRoomId = :chatRoomId AND cm.userId = :userId")
    void updateStatusAndLeftAt(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId,
            @Param("status") ChatMemberStatus status,
            @Param("leftAt") LocalDateTime leftAt
    );

}
