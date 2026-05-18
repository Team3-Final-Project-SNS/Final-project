package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class MeetVerificationController {

    private final MeetVerificationService meetVerificationService;

    // GPS 장소 인증 API
    @PostMapping("/{matchId}/place-verification")
    public ResponseEntity<ApiResponseDto<PlaceVerificationResponseDto>> createPlaceVerification(
            @PathVariable Long matchId,
            @RequestHeader Long userId,
            @Valid @RequestBody PlaceVerificationRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationService.createPlaceVerification(matchId, userId, requestDto)));
    }
}
