package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.chat.dto.response.ChatRoomResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 채팅방 목록 조회
    // TODO: JWT 완성 후 @AuthenticationPrincipal로 userId 추출 예정
    @GetMapping("/chat-rooms")
    public ResponseEntity<ApiResponseDto<List<ChatRoomResponseDto>>> getChatRooms(
            @RequestParam Long userId // 임시: JWT 완성 후 제거 예정
    ) {
        List<ChatRoomResponseDto> response = chatService.getChatRooms(userId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
