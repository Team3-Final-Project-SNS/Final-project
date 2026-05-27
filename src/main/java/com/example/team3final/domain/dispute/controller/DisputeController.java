package com.example.team3final.domain.dispute.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/{matchId}/disputes")
    public ResponseEntity<ApiResponseDto<CreateDisputeResponseDto>> createDispute(

            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody CreateDisputeRequestDto request
    ) {
        // JWT 토큰에서 검증된 userId 추출 (당사자 검증용)
        Long userId = userDetails.getUserId();

        CreateDisputeResponseDto response = disputeService.createDispute(matchId, userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }
}
