package com.example.team3final.domain.chat.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;

import java.util.List;
import java.util.Map;

public interface ChatService {

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    // TODO: 고도화 시 카프카로 교체 예정 → void로 변경될 예정
    Long createChatRoom(Long postId, Long authorId, Long applicantId);

    // 채팅방 즉시 비활성화 - 취소/노쇼 시
    void deactivateChatRoom(Long postId);

    // 채팅방 2시간 후 비활성화 예약 - 만남 인증 완료 시
    void scheduleChatRoomDeactivation(Long postId);

    // 메시지 목록 조회 (커서 기반 페이징)
    CursorResponseDto<ChatMessageResponseDto> getChatMessages(Long chatRoomId, Long userId, Long cursorId, int size);

    // postId로 chatRoomId 조회 - 매칭 상세 조회에서 사용
    Long getChatRoomIdByPostId(Long postId);

    /**
     * postId 목록으로 chatRoomId 일괄 조회 — 매칭 목록(getMatches) N+1 방지용
     */
    Map<Long, Long> getChatRoomIdsByPostIds(List<Long> postIds);

    // 참여자 검증/읽음 처리 없이 전체 메세지 조회
    List<ChatMessageResponseDto> getChatMessagesForAdmin(Long chatRoomId);
}