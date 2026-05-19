package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 채팅방 목록 조회
    // TODO: JWT 완성 후 @AuthenticationPrincipal로 userId 추출 예정
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ChatRoomResponseDto>>> getChatRooms(
            @RequestParam Long userId // 임시: JWT 완성 후 제거 예정
    ) {
        List<ChatRoomResponseDto> response = chatService.getChatRooms(userId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 메시지 목록 조회
    // TODO: JWT 완성 후 @AuthenticationPrincipal로 userId 추출 예정
    @GetMapping("/{chatRoomId}/messages")
    public ResponseEntity<ApiResponseDto<CursorResponseDto<ChatMessageResponseDto>>> getChatMessages(
            @PathVariable Long chatRoomId,
            @RequestParam Long userId,                                 // 임시: JWT 완성 후 제거
            @RequestParam(defaultValue = "9999999999") Long cursorId,  // 첫 요청 시 가장 큰 ID
            @RequestParam(defaultValue = "20") int size                // 한 번에 가져올 메시지 수
    ) {
        CursorResponseDto<ChatMessageResponseDto> response = chatService.getChatMessages(chatRoomId, userId, cursorId, size);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 채팅방 나가기
    // TODO: JWT 완성 후 @AuthenticationPrincipal로 userId 추출 예정
    @PatchMapping("/{chatRoomId}/leave")
    public ResponseEntity<ApiResponseDto<Void>> leaveChatRoom(
            @PathVariable Long chatRoomId,
            @RequestParam Long userId // 임시: JWT 완성 후 제거
    ) {
        chatService.leaveChatRoom(chatRoomId, userId);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
