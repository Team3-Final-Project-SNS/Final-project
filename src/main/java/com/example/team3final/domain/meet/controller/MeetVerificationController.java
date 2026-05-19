package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.dto.response.QrScanResponseDto;
import com.example.team3final.domain.meet.service.MeetVerificationServiceImpl;
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
public class MeetVerificationController {

    private final MeetVerificationServiceImpl meetVerificationService;

    // GPS 장소 인증 API
    @PostMapping("/{matchId}/place-verification")
    public ResponseEntity<ApiResponseDto<PlaceVerificationResponseDto>> createPlaceVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PlaceVerificationRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
                meetVerificationService.createPlaceVerification(matchId, userId, requestDto)));
    }

    // QR 토큰 발급/조회
    @GetMapping("/{matchId}/qr")
    public ResponseEntity<ApiResponseDto<QrResponseDto>> getMeetQr(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetQr(matchId, userId)));
    }

    // QR 스캔
    @PostMapping("/{matchId}/qr/scan")
    public ResponseEntity<ApiResponseDto<QrScanResponseDto>> createQrScan(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody QrScanRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
                meetVerificationService.createQrScan(matchId, userId, requestDto)));
    }

    // QR 인증 상태 조회
    @GetMapping("/{matchId}/verification")
    public ResponseEntity<ApiResponseDto<MeetVerificationResponseDto>> getMeetVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetVerification(matchId, userId)));
    }
}
