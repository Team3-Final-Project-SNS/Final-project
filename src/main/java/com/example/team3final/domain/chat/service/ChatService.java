package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;

import java.util.List;

public interface ChatService {

    // TODO: Match 도메인 구현 완료 후 authorId, applicantId 파라미터 삭제 예정
    // TODO: 고도화 시 카프카로 교체 예정 → void로 변경될 예정
    Long createChatRoom(Long matchId, Long authorId, Long applicantId);

    // 채팅방 비활성화 - 완료/취소/노쇼 시 내부 호출
    void deactivateChatRoom(Long matchId);

    // 채팅방 목록 조회
    List<ChatRoomResponseDto> getChatRooms(Long userId);

    // 메시지 목록 조회 (커서 기반 페이징)
    CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size);

    // 채팅방 나가기 - 완료/취소/노쇼 후에만 가능
    void leaveChatRoom(Long chatRoomId, Long userId);
}
