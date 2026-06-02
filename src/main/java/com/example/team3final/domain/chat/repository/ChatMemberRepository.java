package com.example.team3final.domain.chat.repository;

import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

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

    // 신청자 매칭 취소 시 해당 참여자만 채팅방에서 제거
    void deleteByChatRoomIdAndUserId(Long chatRoomId, Long userId);

}
