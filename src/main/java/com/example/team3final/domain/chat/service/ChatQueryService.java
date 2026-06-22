package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;

import java.util.List;

// Chat 도메인의 채팅 메시지/참여자 조회 기능을 담당하는 서비스
public interface ChatQueryService {

    // 메시지 목록 조회 (커서 기반 페이징)
    CursorResponseDto<ChatMessageResponseDto> getChatMessages(
            Long chatRoomId,
            Long userId,
            Long cursorId,
            int size
    );

    // 채팅방 참여자 목록 조회 - 채팅방 멤버만 접근 가능
    List<ChatMemberResponseDto> getChatMembers(Long chatRoomId, Long userId);
}
