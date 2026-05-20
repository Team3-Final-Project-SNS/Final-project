package com.example.team3final.domain.location.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class UserLocationController {

    private final UserLocationService userLocationService;

    // 내 위치 업데이트 - 1초마다 호출
    @PutMapping("/{matchId}/location")
    public ResponseEntity<ApiResponseDto<UpdateLocationResponseDto>> updateMyLocation(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateLocationRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                userLocationService.updateMyLocation(matchId, userId, requestDto)));
    }

    // 양측 위치 조회 - 1초 마다 풀링
    @GetMapping("/{matchId}/location")
    public ResponseEntity<ApiResponseDto<GetLocationResponseDto>> getLocations(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                userLocationService.getLocations(matchId, userId)));
    }
}
