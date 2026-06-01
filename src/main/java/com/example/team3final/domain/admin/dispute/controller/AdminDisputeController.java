package com.example.team3final.domain.admin.dispute.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminDisputeController {

    private final AdminDisputeService adminDisputeService;

    // 이의제기 상세 조회
    @GetMapping("/disputes/{disputeId}")
    public ResponseEntity<ApiResponseDto<GetAdminDisputeResponseDto>> getDispute(
            @PathVariable Long disputeId,
            @AuthenticationPrincipal AdminDetailsImpl adminDetails) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminDisputeService.getDispute(adminId ,disputeId)));
    }

    // 이의제기 목록 조회
    @GetMapping("/disputes")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetAdminDisputesResponseDto>>> getDisputes(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) DisputeStatus status) {

        Long adminId = adminDetails.getAdminId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponseDto.success(adminDisputeService.getDisputes(adminId, status, pageable)));
    }

    // 이의제기 최종 판정
    @PatchMapping("/disputes/{disputeId}/judge")
    public ResponseEntity<ApiResponseDto<AdminJudgeDisputeResponseDto>> judgeDispute(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long disputeId,
            @Valid @RequestBody AdminJudgeDisputeRequestDto requestDto) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminDisputeService.judgeDispute(adminId ,disputeId, requestDto)));
    }
}
