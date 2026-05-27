package com.example.team3final.domain.ai.matching.service;


import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.dto.response.AiMatchingChatResponseDto;

public interface AiMatchingService {

    AiMatchingChatResponseDto chat(String email, AiMatchingChatRequestDto request);
}
