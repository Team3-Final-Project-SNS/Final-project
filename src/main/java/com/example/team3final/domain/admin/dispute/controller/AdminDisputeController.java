package com.example.team3final.domain.admin.dispute.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
