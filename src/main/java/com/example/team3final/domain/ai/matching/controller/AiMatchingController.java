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

    /**
     * 매칭 AI 채팅 요청을 처리합니다.
     *
     * 로그인한 사용자의 이메일을 SecurityContext의 Authentication에서 가져오고,
     * 사용자가 입력한 자연어 식사 조건을 매칭 AI 서비스로 전달합니다.
     *
     * 매칭 AI 서비스는 같은 학교의 모집 중인 식사팟 후보를 조회한 뒤,
     * 사용자의 조건과 후보 정보를 기반으로 자연어 추천 응답을 생성합니다.
     *
     * @param authentication 현재 로그인한 사용자의 인증 정보
     * @param request 사용자의 자연어 요청과 대화 세션 ID
     * @return AI 추천 답변, 추천 후보 게시글 목록, fallback 사용 여부
     */

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

