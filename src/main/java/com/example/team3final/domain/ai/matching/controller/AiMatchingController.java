package com.example.team3final.domain.ai.matching.controller;


import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.dto.response.AiMatchingChatResponseDto;
import com.example.team3final.domain.ai.matching.service.AiMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/matching")
public class AiMatchingController {


    private final AiMatchingService aiMatchingService;

    /**
     * 매칭 AI 스트리밍 응답
     *
     * SSE 기반으로 AI 응답을 토큰 단위 또는 문장 단위로 전송합니다.
     * 추후에 확장 예정. 먼저 chat기반으로 만든 후에 확장한다.
     */
//    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter streamChat(
//            Authentication authentication,
//            @RequestBody AiMatchingChatRequestDto request
//    ) {
//        String email = authentication.getName();
//
//        return aiMatchingService.streamChat(email, request);
//    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponseDto<AiMatchingChatResponseDto>> chat(
            Authentication authentication,
            @Valid @RequestBody AiMatchingChatRequestDto request
    ) {
        String email = authentication.getName();

        AiMatchingChatResponseDto response = aiMatchingService.chat(email, request);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

}

