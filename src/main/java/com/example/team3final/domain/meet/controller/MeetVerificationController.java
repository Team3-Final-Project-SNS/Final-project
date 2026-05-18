package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.service.MeetVerificationServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class MeetVerificationController {

    private final MeetVerificationServiceImpl meetVerificationService;

    // GPS 장소 인증 API
    @PostMapping("/{matchId}/place-verification")
    public ResponseEntity<ApiResponseDto<PlaceVerificationResponseDto>> createPlaceVerification(
            @PathVariable Long matchId,
            @RequestHeader Long userId,
            @Valid @RequestBody PlaceVerificationRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.createPlaceVerification(matchId, userId, requestDto)));
    }

    // Qr토큰 발급/조회
    @GetMapping("/{matchId}/qr")
    public ResponseEntity<ApiResponseDto<QrResponseDto>> getMeetQr(
            @PathVariable Long matchId,
            @RequestHeader Long userId) {
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetQr(matchId, userId)));
    }
}
