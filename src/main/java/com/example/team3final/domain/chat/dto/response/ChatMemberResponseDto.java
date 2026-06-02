package com.example.team3final.domain.chat.dto.response;

import com.example.team3final.domain.chat.entity.ChatMember;

// 채팅방 참여자 정보 응답 DTO
public record ChatMemberResponseDto(
        Long userId,      // 참여자 유저 ID
        String nickname   // 참여자 닉네임
) {
    public static ChatMemberResponseDto of(ChatMember member, String nickname) {
        return new ChatMemberResponseDto(
                member.getUserId(),
                nickname
        );
    }
}