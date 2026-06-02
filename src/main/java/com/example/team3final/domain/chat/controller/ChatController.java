package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMemberResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
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

    // 메시지 목록 조회
    @GetMapping("/{chatRoomId}/messages")
    public ResponseEntity<ApiResponseDto<CursorResponseDto<ChatMessageResponseDto>>> getChatMessages(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "9999999999") Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId();
        CursorResponseDto<ChatMessageResponseDto> response = chatService.getChatMessages(
                chatRoomId, userId, cursorId, size);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 채팅방 참여자 목록 조회
    @GetMapping("/{chatRoomId}/members")
    public ResponseEntity<ApiResponseDto<List<ChatMemberResponseDto>>> getChatMembers(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUserId();
        List<ChatMemberResponseDto> response = chatService.getChatMembers(chatRoomId, userId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

}