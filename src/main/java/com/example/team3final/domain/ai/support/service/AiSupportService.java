package com.example.team3final.domain.ai.support.service;

import com.example.team3final.domain.ai.support.dto.request.AiSupportChatRequestDto;
import com.example.team3final.domain.ai.support.dto.response.AiSupportChatResponseDto;
import com.example.team3final.domain.ai.support.dto.response.AiSupportSessionTokenStatsDto;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 고객센터 AI 채팅 기능의 서비스 계약입니다.
 *
 * 구현체는 LLM 호출뿐 아니라 대화 저장, RAG 검색, Tool 바인딩,
 * fallback 응답, AI 호출 메트릭 저장까지 하나의 요청 흐름으로 처리합니다.
 */
public interface AiSupportService {

    /**
     * 로그인 사용자의 고객센터 자연어 문의를 처리합니다.
     *
     * @param userId JWT에서 확인한 사용자 ID
     * @param email JWT에서 확인한 사용자 email. LLM Tool 파라미터로 받지 않고 서버가 고정합니다.
     * @param request 사용자 메시지와 conversationId
     * @return 프론트에 보여줄 답변, 카테고리, 요약, fallback 여부
     */
    AiSupportChatResponseDto chat(Long userId, String email, AiSupportChatRequestDto request);

    /**
     * 로그인 사용자의 고객센터 자연어 문의를 SSE 스트리밍으로 처리합니다.
     *
     * 일반 chat()과 같은 프롬프트, RAG, Tool Calling 흐름을 사용하되
     * 프론트가 답변 텍스트를 실시간으로 그릴 수 있도록 Flux<String>으로 반환합니다.
     */
    Flux<String> streamChat(Long userId, String email, AiSupportChatRequestDto request);

    /**
     * 로그인 사용자의 고객센터 AI 세션별 토큰 누적량을 조회합니다.
     */
    List<AiSupportSessionTokenStatsDto> getSessionTokenStats(Long userId);
}
