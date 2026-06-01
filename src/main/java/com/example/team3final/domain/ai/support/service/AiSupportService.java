package com.example.team3final.domain.ai.support.service;

import com.example.team3final.domain.ai.support.dto.request.AiSupportChatRequestDto;
import com.example.team3final.domain.ai.support.dto.response.AiSupportChatResponseDto;

/**
 * 고객센터 AI 채팅 기능의 서비스 계약입니다.
 */
public interface AiSupportService {

    AiSupportChatResponseDto chat(Long userId, String email, AiSupportChatRequestDto request);
}
