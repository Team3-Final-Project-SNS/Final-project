package com.example.team3final.domain.ai.report.controller;


import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.ai.report.dto.request.AiReportChatRequestDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportAnalysisResponseDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportChatResponseDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportHighRiskUsersResponseDto;
import com.example.team3final.domain.ai.report.service.AiReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자용 신고 AI 분석 API 컨트롤러입니다.
 *
 * 특정 신고에 대한 AI 분석 요청과 누적 신고 기반 고위험 유저 조회 요청을 받아
 * 서비스 계층으로 전달하고 공통 응답 형식으로 반환합니다.
 *
 * 컨트롤 흐름:
 * 1. /chat은 관리자의 자연어 메시지를 받아 LLM 의도 분류를 수행합니다.
 * 2. 의도가 단일 신고 분석이면 기존 /{reportId}/analysis 흐름과 같은 서비스 로직을 재사용합니다.
 * 3. 의도가 고위험 유저 조회이면 기존 /high-risk-users 흐름과 같은 서비스 로직을 재사용합니다.
 * 4. 신고 ID나 조회 조건이 부족하면 실행하지 않고 관리자에게 필요한 정보를 다시 요청합니다.
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/reports")
public class AiReportController {

    private final AiReportService aiReportService;

    /**
     * 하나의 신고 AI 챗봇 입구입니다.
     *
     * 프론트는 별도 버튼 없이 이 API로 관리자 메시지만 보내면 되고,
     * 서비스 계층이 메시지 의도를 판단해 신고 분석 또는 고위험 유저 조회를 자동 실행합니다.
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponseDto<AiReportChatResponseDto>> chat(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @Valid @RequestBody AiReportChatRequestDto request
    ) {
        Long adminId = adminDetails.getAdminId();

        return ResponseEntity.ok(ApiResponseDto.success(
                aiReportService.chat(adminId, request)
        ));
    }

    @PostMapping("/{reportId}/analysis")
    public ResponseEntity<ApiResponseDto<AiReportAnalysisResponseDto>> analyzeReport(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long reportId
    ) {
        Long adminId = adminDetails.getAdminId();

        return ResponseEntity.ok(ApiResponseDto.success(
                aiReportService.analyzeReport(adminId, reportId)
        ));
    }

    @GetMapping("/high-risk-users")
    public ResponseEntity<ApiResponseDto<AiReportHighRiskUsersResponseDto>> getHighRiskUsers(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(defaultValue = "5") int limit
    ) {
        Long adminId = adminDetails.getAdminId();

        return ResponseEntity.ok(ApiResponseDto.success(
                aiReportService.getHighRiskUsers(adminId, limit)
        ));
    }
}