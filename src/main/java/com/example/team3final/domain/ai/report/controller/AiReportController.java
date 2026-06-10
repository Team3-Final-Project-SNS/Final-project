package com.example.team3final.domain.ai.report.controller;


import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.ai.report.dto.request.AiReportChatRequestDto;
import com.example.team3final.domain.ai.report.service.AiReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 관리자 콘솔 AI 챗봇 API 컨트롤러입니다.
 *
 * 관리자 자연어 메시지를 서비스 계층으로 전달하고,
 * SSE 스트리밍으로 답변 본문을 반환합니다.
 *
 * 컨트롤 흐름:
 * 1. /chat/stream은 관리자의 자연어 메시지를 받습니다.
 * 2. 서비스 계층이 관리자 Tool, RAG 정책 문서, GPT 일반 응답 전략을 선택합니다.
 * 3. 관리자 화면에는 자연어 답변 본문만 실시간으로 전송합니다.
 */

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/admin/ai/reports", "/api/v1/admin/ai/console"})
public class AiReportController {

    private final AiReportService aiReportService;

    /**
     * 관리자 콘솔 AI 답변을 SSE로 스트리밍합니다.
     *
     * 컨트롤 흐름:
     * 1. 관리자 인증 정보를 확인합니다.
     * 2. 자연어 메시지를 서비스 계층으로 전달합니다.
     * 3. 서비스가 관리자 Tool, RAG 정책 문서, GPT 일반 응답 전략을 연결합니다.
     * 4. LLM 답변 본문을 text/event-stream으로 실시간 전송합니다.
     *
     *
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE.
     * “이 컨트롤러는 실시간 채팅처럼 조금씩 응답을 보낼 거다” 라는 표시
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @Valid @RequestBody AiReportChatRequestDto request
    ) {
        return aiReportService.streamChat(adminDetails.getAdminId(), request);
    }
}
