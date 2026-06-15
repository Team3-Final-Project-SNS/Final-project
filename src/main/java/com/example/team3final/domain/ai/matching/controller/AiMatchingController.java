package com.example.team3final.domain.ai.matching.controller;


import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.service.AiMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/matching")
public class AiMatchingController {


    private final AiMatchingService aiMatchingService;

    /**
     * 매칭 AI 답변을 SSE로 스트리밍합니다.
     *
     * 컨트롤 흐름:
     * 1. SecurityContext에서 로그인 사용자 email을 확인합니다.
     * 2. 서비스가 사용자 조건을 Rewrite Query로 정리합니다.
     * 3. LLM이 매칭 Tool을 직접 호출해 후보를 조회합니다.
     * 4. 생성되는 답변 텍스트를 text/event-stream으로 실시간 전송합니다.
     *
     *
     *  produces = MediaType.TEXT_EVENT_STREAM_VALUE.
     * "이 컨트롤러는 실시간 채팅처럼 조금씩 응답을 보낼 거다” 라는 표시
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            Authentication authentication,
            @Valid @RequestBody AiMatchingChatRequestDto request
    ) {
        String email = authentication.getName();

        return aiMatchingService.streamChat(email, request);
    }

    @DeleteMapping("/chat/{conversationId}")
    public ResponseEntity<Void> clearConversation(
            Authentication authentication,
            @PathVariable String conversationId
    ) {
        // 매칭 AI 화면을 나갈 때 프론트가 호출해 대화 메모리와 직전 추천 ID를 함께 정리합니다.
        // 다음 진입 시 이전 추천 결과가 새 추천 스코프에 섞이지 않도록 하기 위한 API입니다.
        String email = authentication.getName();

        aiMatchingService.clearConversation(email, conversationId);

        return ResponseEntity.noContent().build();
    }

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

//    // SSE 전환 전 JSON 응답용 엔드포인트입니다.
//    // @PostMapping("/chat")
//    public ResponseEntity<ApiResponseDto<AiMatchingChatResponseDto>> createAiMatchingChat(
//            Authentication authentication,
//            @Valid @RequestBody AiMatchingChatRequestDto request
//    ) {
//        String email = authentication.getName();
//
//        AiMatchingChatResponseDto response = aiMatchingService.createAiMatchingChat(email, request);
//
//        return ResponseEntity.ok(ApiResponseDto.success(response));
//    }

}
