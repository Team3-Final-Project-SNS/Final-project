package com.example.team3final.domain.ai.matching.service;


import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.dto.response.AiMatchingChatResponseDto;

public interface AiMatchingService {

    /**
     * 매칭 AI 일반 채팅 응답을 생성합니다.
     *
     * @param email 로그인한 사용자 이메일
     * @param request 사용자의 자연어 식사 조건 요청
     * @return AI 추천 답변, 추천 후보 목록, fallback 여부
     */
    AiMatchingChatResponseDto createAiMatchingChat(String email, AiMatchingChatRequestDto request);
}
