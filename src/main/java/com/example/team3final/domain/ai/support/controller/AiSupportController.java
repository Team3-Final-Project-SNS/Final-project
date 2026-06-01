package com.example.team3final.domain.ai.support.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.ai.support.dto.request.AiSupportChatRequestDto;
import com.example.team3final.domain.ai.support.dto.response.AiSupportChatResponseDto;
import com.example.team3final.domain.ai.support.service.AiSupportService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객센터 AI 챗봇 API 컨트롤러입니다.
 *
 * 로그인 사용자의 자연어 문의를 받아 고객센터 AI 서비스로 전달하고,
 * AI 답변과 대화 ID를 공통 응답 형식으로 반환합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/support")
public class AiSupportController {

    private final AiSupportService aiSupportService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponseDto<AiSupportChatResponseDto>> chat(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AiSupportChatRequestDto request
    ) {
        AiSupportChatResponseDto response = aiSupportService.chat(
                userDetails.getUserId(),
                userDetails.getEmail(),
                request
        );

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
