package com.example.team3final.domain.ai.support.controller;

import com.example.team3final.domain.ai.support.dto.request.AiSupportChatRequestDto;
import com.example.team3final.domain.ai.support.service.AiSupportService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 고객센터 AI 챗봇 API 컨트롤러입니다.
 *
 * 로그인 사용자의 자연어 문의를 받아 고객센터 AI 서비스로 전달하고,
 * AI 답변과 대화 ID를 공통 응답 형식으로 반환합니다.
 *
 * 프론트는 최초 요청에서 conversationId를 비워 보내고,
 * 응답으로 받은 conversationId를 다음 요청부터 함께 보내면 멀티턴 맥락이 유지됩니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/support")
public class AiSupportController {

    private final AiSupportService aiSupportService;

//    // SSE 전환 전 JSON 응답용 엔드포인트입니다.
//    // @PostMapping("/chat")
//    public ResponseEntity<ApiResponseDto<AiSupportChatResponseDto>> chat(
//            @AuthenticationPrincipal UserDetailsImpl userDetails,
//            @Valid @RequestBody AiSupportChatRequestDto request
//    ) {
//        // userId/email은 JWT 인증 결과에서 가져옵니다.
//        // 특히 email은 Tool 호출 시 현재 사용자 컨텍스트 조회에 사용되므로 request body로 받지 않습니다.
//        AiSupportChatResponseDto response = aiSupportService.chat(
//                userDetails.getUserId(),
//                userDetails.getEmail(),
//                request
//        );
//
//        return ResponseEntity.ok(ApiResponseDto.success(response));
//    }

    /**
     * 고객센터 AI 답변을 SSE로 스트리밍합니다.
     *
     * 컨트롤 흐름:
     * 1. JWT에서 userId/email을 확인합니다.
     * 2. 요청 본문에는 사용자의 메시지와 conversationId만 받습니다.
     * 3. 서비스 계층이 기존 고객센터 AI 흐름처럼 프롬프트, RAG, Tool Calling을 준비합니다.
     * 4. ChatClient.stream().content() 결과를 text/event-stream으로 그대로 흘려보냅니다.
     *
     * 프론트는 별도 버튼 없이 이 엔드포인트를 호출하면 토큰이 도착하는 대로
     * 채팅 말풍선에 이어 붙일 수 있습니다.
     *
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE.
     * “이 컨트롤러는 실시간 채팅처럼 조금씩 응답을 보낼 거다” 라는 표시
     *
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AiSupportChatRequestDto request
    ) {
        return aiSupportService.streamChat(
                userDetails.getUserId(),
                userDetails.getEmail(),
                request
        );
    }
}
