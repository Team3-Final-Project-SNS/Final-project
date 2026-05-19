package com.example.team3final.domain.chat.service;

import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;

import java.util.List;

public interface ChatService {

    // TODO: Match 도메인 구현 완료 후 authorId, applicantId 파라미터 삭제 예정
    // 채팅방 생성 - 매칭 확정 시 내부 호출
    void createChatRoom(Long matchId, Long authorId, Long applicantId);

    // 채팅방 비활성화 - 완료/취소/노쇼 시 내부 호출
    void deactivateChatRoom(Long matchId);

    // 채팅방 목록 조회
    List<ChatRoomResponseDto> getChatRooms(Long userId);
}
