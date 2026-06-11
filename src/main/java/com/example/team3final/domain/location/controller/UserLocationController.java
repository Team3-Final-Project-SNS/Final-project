package com.example.team3final.domain.location.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Location", description = "위치 API - GPS 인증 화면에서 실시간 위치 업데이트 및 상대방 위치 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class UserLocationController {

    private final UserLocationService userLocationService;

    // 공통 에러 응답 예시 상수

    // 401: 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 403: 매칭 당사자 아님
    private static final String EXAMPLE_403_NOT_PARTICIPANT = """
            {
              "success": false,
              "code": "MATCH_NOT_PARTICIPANT",
              "message": "해당 매칭의 당사자가 아닙니다.",
              "data": null
            }
            """;

    // 404: 매칭 없음
    private static final String EXAMPLE_404_MATCH = """
            {
              "success": false,
              "code": "MATCH_001",
              "message": "존재하지 않는 매칭입니다.",
              "data": null
            }
            """;

    // 내 위치 업데이트 - 5초마다 호출
    @Operation(
            summary = "내 위치 업데이트",
            description = """
                    GPS 인증 화면에서 내 현재 위치를 Redis에 업데이트합니다.
                    
                    **호출 주기:** 프론트에서 **5초마다** 폴링 방식으로 호출합니다.
                    
                    **위치 데이터 저장:** Redis에 TTL 30초로 저장됩니다.
                    (30초 이내 업데이트 없으면 자동 만료 = 위치 공유 종료)
                    
                    **사용 목적:** GPS 인증 화면에서 약속 장소 반경 지도에
                    본인과 상대방 위치를 파란 점으로 표시합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "위치 업데이트 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "매칭 당사자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_PARTICIPANT))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            )
    })
    @PutMapping("/{matchId}/location")
    public ResponseEntity<ApiResponseDto<UpdateLocationResponseDto>> updateMyLocation(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateLocationRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                userLocationService.updateMyLocation(matchId, userId, requestDto)));
    }

    // 양측 위치 조회 - 5초 마다 풀링
    @Operation(
            summary = "양측 위치 조회",
            description = """
                    GPS 인증 화면에서 나와 상대방의 현재 위치를 조회합니다.
                    
                    **호출 주기:** 프론트에서 **5초마다** 폴링 방식으로 호출합니다.
                    
                    **상대방 위치가 null인 경우:**
                    - 상대방이 아직 위치를 공유하지 않았거나
                    - Redis TTL 30초가 만료된 경우 (앱 종료 또는 백그라운드 전환)
                    
                    **활용:** 지도에 본인(파란 점)과 상대방(파란 점) 위치를 표시하여
                    약속 장소 반경 50m 진입 여부를 시각적으로 안내합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (상대방 위치 없으면 null 반환)"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "매칭 당사자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_PARTICIPANT))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            )
    })
    @GetMapping("/{matchId}/location")
    public ResponseEntity<ApiResponseDto<GetLocationResponseDto>> getLocations(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                userLocationService.getLocations(matchId, userId)));
    }
}
