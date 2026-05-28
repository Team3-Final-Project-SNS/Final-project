package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
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
                meetVerificationService.createPlaceVerification(userId, matchId, requestDto)));
    }

    // QR 토큰 발급/조회
    @GetMapping("/{matchId}/qr")
    public ResponseEntity<ApiResponseDto<QrResponseDto>> getMeetQr(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetQr(userId, matchId)));
    }

    // QR 스캔
    @PostMapping("/{matchId}/qr/scan")
    public ResponseEntity<ApiResponseDto<QrScanResponseDto>> createQrScan(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody QrScanRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
                meetVerificationService.createQrScan(userId, matchId, requestDto)));
    }

    // QR 인증 상태 조회
    @GetMapping("/{matchId}/verification")
    public ResponseEntity<ApiResponseDto<MeetVerificationResponseDto>> getMeetVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetVerification(userId, matchId)));
    }

    // 만남 시간 연장 요청
    @PostMapping("/{matchId}/extension/request")
    public ResponseEntity<ApiResponseDto<CreateMeetExtensionResponseDto>> createMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(meetVerificationService.createMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 수락
    @PatchMapping("/{matchId}/extension/accept")
    public ResponseEntity<ApiResponseDto<AcceptMeetExtensionResponseDto>> acceptMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.acceptMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 거절
    @PatchMapping("/{matchId}/extension/reject")
    public ResponseEntity<ApiResponseDto<RejectMeetExtensionResponseDto>> rejectMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.rejectMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 상태 조회
    @GetMapping("/{matchId}/extension")
    public ResponseEntity<ApiResponseDto<GetMeetExtensionResponseDto>> getMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.getMeetExtension(userId, matchId)));
    }
}
