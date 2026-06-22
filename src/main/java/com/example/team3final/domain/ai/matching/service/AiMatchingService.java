package com.example.team3final.domain.ai.matching.service;


import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.dto.response.AiMatchingChatResponseDto;
import reactor.core.publisher.Flux;

public interface AiMatchingService {

    /**
     * 매칭 AI 일반 채팅 응답을 생성합니다.
     *
     * @param email 로그인한 사용자 이메일
     * @param request 사용자의 자연어 식사 조건 요청
     * @return AI 추천 답변, 추천 후보 목록, fallback 여부
     */
    AiMatchingChatResponseDto createAiMatchingChat(String email, AiMatchingChatRequestDto request);

    /**
     * 매칭 AI 답변을 SSE 스트리밍으로 생성합니다.
     *
     * 기존 매칭 AI의 Rewrite Query, 프롬프트 주입, Tool Calling 흐름을 유지하면서
     * 프론트가 답변 텍스트를 실시간으로 표시할 수 있도록 Flux<String>으로 반환합니다.
     */
    Flux<String> streamChat(String email, AiMatchingChatRequestDto request);

    /**
     * 사용자가 매칭 AI 화면을 떠날 때 현재 대화 세션 메모리와 추천 기록을 정리합니다.
     *
     * 추천 기록을 지우지 않으면 다음 진입 후 "아까 것 중에서" 같은 후속 질문이
     * 이전 세션의 게시글 ID를 기준으로 처리될 수 있습니다.
     */
    void clearConversation(String email, String conversationId);
}
