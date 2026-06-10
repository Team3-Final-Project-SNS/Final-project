package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MeetVerificationController {

    private final MeetVerificationService meetVerificationService;

    // GPS 장소 인증 API
    @PostMapping("/matches/{matchId}/place-verification")
    public ResponseEntity<ApiResponseDto<PlaceVerificationResponseDto>> createPlaceVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PlaceVerificationRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.createPlaceVerification(userId, matchId, requestDto)));
    }

    // QR 토큰 발급/조회
    @GetMapping("/posts/{postId}/qr")
    public ResponseEntity<ApiResponseDto<QrResponseDto>> getMeetQrByPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetQrByPost(userId, postId)));
    }

    // QR 스캔
    @PostMapping("/matches/{matchId}/qr/scan")
    public ResponseEntity<ApiResponseDto<QrScanResponseDto>> createQrScan(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody QrScanRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.createQrScan(userId, matchId, requestDto)));
    }

    // QR 인증 상태 조회
    @GetMapping("/matches/{matchId}/verification")
    public ResponseEntity<ApiResponseDto<MeetVerificationResponseDto>> getMeetVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.getMeetVerification(userId, matchId)));
    }

    // 만남 시간 연장 요청
    @PostMapping("/matches/{matchId}/extension/request")
    public ResponseEntity<ApiResponseDto<CreateMeetExtensionResponseDto>> createMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(meetVerificationService.createMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 수락
    @PatchMapping("/matches/{matchId}/extension/accept")
    public ResponseEntity<ApiResponseDto<AcceptMeetExtensionResponseDto>> acceptMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.acceptMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 거절
    @PatchMapping("/matches/{matchId}/extension/reject")
    public ResponseEntity<ApiResponseDto<RejectMeetExtensionResponseDto>> rejectMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.rejectMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 상태 조회
    @GetMapping("/matches/{matchId}/extension")
    public ResponseEntity<ApiResponseDto<GetMeetExtensionResponseDto>> getMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationService.getMeetExtension(userId, matchId)));
    }
}
