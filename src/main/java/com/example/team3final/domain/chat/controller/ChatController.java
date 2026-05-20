package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 채팅방 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ChatRoomResponseDto>>> getChatRooms(
            @AuthenticationPrincipal UserDetailsImpl userDetails       // JWT에서 자동 추출
    ) {
        List<ChatRoomResponseDto> response = chatService.getChatRooms(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 메시지 목록 조회
    @GetMapping("/{chatRoomId}/messages")
    public ResponseEntity<ApiResponseDto<CursorResponseDto<ChatMessageResponseDto>>> getChatMessages(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,      // JWT에서 자동 추출
            @RequestParam(defaultValue = "9999999999") Long cursorId,  // 첫 요청 시 가장 큰 ID
            @RequestParam(defaultValue = "20") int size                // 한 번에 가져올 메시지 수
    ) {
        CursorResponseDto<ChatMessageResponseDto> response = chatService.getChatMessages(
                chatRoomId, userDetails.getUserId(), cursorId, size);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 채팅방 나가기
    @PatchMapping("/{chatRoomId}/leave")
    public ResponseEntity<ApiResponseDto<Void>> leaveChatRoom(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails // JWT에서 자동 추출
    ) {
        chatService.leaveChatRoom(chatRoomId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
