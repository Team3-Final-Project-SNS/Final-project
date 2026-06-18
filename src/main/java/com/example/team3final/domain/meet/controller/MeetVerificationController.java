package com.example.team3final.domain.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.service.MeetVerificationCommandService;
import com.example.team3final.domain.meet.service.MeetVerificationQueryService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MeetVerification", description = "만남 인증 API - GPS 장소 인증, QR 인증, 만남 시간 연장")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MeetVerificationController {

    private final MeetVerificationCommandService meetVerificationCommandService;
    private final MeetVerificationQueryService meetVerificationQueryService;

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

    // 422: 인증 가능 시간 아님
    private static final String EXAMPLE_422_NOT_VERIFICATION_TIME = """
            {
              "success": false,
              "code": "VERIFY_003",
              "message": "현재는 장소 인증 가능 시간이 아닙니다.",
              "data": null
            }
            """;

    // GPS 장소 인증 API
    @Operation(
            summary = "GPS 장소 인증",
            description = """
                    약속 장소 반경 50m 내에서 GPS 위치를 인증합니다.
                    
                    **인증 가능 시간:** 약속 시간 10분 전 ~ 약속 시간 후 10분
                    (서버 검증 반경: GPS 오차 고려 60m)
                    
                    **인증 흐름:**
                    1. 등록자 GPS 인증 완료 또는 약속 시간 10분 경과 → 등록자 QR 단계 진입 가능
                    2. GPS 장소 인증을 완료한 신청자가 QR 스캔 → 만남 인증 완료 (DONE)
                    3. GPS 장소 인증을 하지 않은 신청자는 QR 만남 인증 현황에서 제외
                    
                    **단체 만남:** 등록자 GPS 인증 시 같은 게시글의 모든 매칭에 전파됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "GPS 인증 성공"),
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 GPS 인증 완료 (VERIFY_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_004",
                                      "message": "이미 인증을 완료했습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "인증 가능 시간 아님(VERIFY_003) / 반경 50m 초과(VERIFY_002)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_002",
                                      "message": "약속 장소 반경 50m 밖에 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/matches/{matchId}/place-verification")
    public ResponseEntity<ApiResponseDto<PlaceVerificationResponseDto>> createPlaceVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PlaceVerificationRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationCommandService.createPlaceVerification(userId, matchId, requestDto)));
    }

    // QR 토큰 발급/조회
    @Operation(
            summary = "만남 QR 코드 조회 (등록자 전용)",
            description = """
                    등록자가 게시글 기준으로 만남 QR 토큰을 조회합니다.
                    
                    **호출 조건:** GPS 장소 인증(VERIFIED) 완료 후에만 발급됩니다.
                    
                    **QR 유효시간:** 양측 GPS 인증 완료 시각부터 30분
                    (만남 시간 연장 수락 시 TTL 재설정)
                    
                    **단체 만남:** 게시글 기준 공통 QR 하나가 모든 신청자에게 공유됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR 토큰 조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "등록자만 QR 조회 가능 (VERIFY_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_005",
                                      "message": "등록자만 QR을 발급받을 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "GPS 장소 인증 미완료 (VERIFY_006) / QR 만료 (VERIFY_007)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_006",
                                      "message": "장소 인증이 선행되어야 합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/posts/{postId}/qr")
    public ResponseEntity<ApiResponseDto<QrResponseDto>> getMeetQrByPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationQueryService.getMeetQrByPost(userId, postId)));
    }

    // QR 스캔
    @Operation(
            summary = "QR 코드 스캔 (신청자 전용)",
            description = """
                    신청자가 등록자의 QR 코드를 스캔하여 만남 인증을 완료합니다.
                    
                    **스캔 완료 시:**
                    - 인증 상태 → DONE
                    - 매칭 상태 → COMPLETED
                    - 예치 포인트 전액 반환
                    - 채팅방 읽기 전용 전환 예약 (2시간 후)
                    - 후기 작성 알림 발송
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR 스캔 성공 - 만남 인증 완료, 예치 포인트 반환"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "신청자만 QR 스캔 가능 (VERIFY_008)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_008",
                                      "message": "신청자만 QR을 스캔할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "유효하지 않은 QR 토큰(VERIFY_009) / QR 만료(VERIFY_007)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_009",
                                      "message": "유효하지 않은 QR 토큰입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/matches/{matchId}/qr/scan")
    public ResponseEntity<ApiResponseDto<QrScanResponseDto>> createQrScan(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody QrScanRequestDto requestDto) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationCommandService.createQrScan(userId, matchId, requestDto)));
    }

    // QR 인증 상태 조회
    @Operation(
            summary = "만남 인증 상태 조회",
            description = """
                    매칭의 현재 인증 상태를 조회합니다.
                    
                    **인증 상태값:**
                    - `PENDING` : 인증 대기 (GPS 인증 전)
                    - `VERIFIED` : GPS 양측 인증 완료 (QR 단계 진입)
                    - `DONE` : 만남 인증 완료
                    - `HOST_NO_SHOW` : 등록자 노쇼 예정
                    - `GUEST_NO_SHOW` : 신청자 노쇼 예정
                    - `BOTH_NO_SHOW` : 양측 노쇼 예정
                    - `DISPUTE` : 이의제기 진행 중
                    - `NO_SHOW_CONFIRMED` : 노쇼 확정
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
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
    @GetMapping("/matches/{matchId}/verification")
    public ResponseEntity<ApiResponseDto<MeetVerificationResponseDto>> getMeetVerification(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(
                meetVerificationQueryService.getMeetVerification(userId, matchId)));
    }

    // 만남 시간 연장 요청
    @Operation(
            summary = "만남 시간 연장 요청 (신청자 전용)",
            description = """
                    신청자가 약속 시간 15분 연장을 요청합니다.
                    
                    **요청 가능 조건:**
                    - MATCHED 상태의 매칭만 가능합니다.
                    - 약속 시간 5분 전까지만 요청 가능합니다.
                    - 1회만 사용 가능합니다.
                    - 진행 중인 연장 요청이 없어야 합니다.
                    
                    **요청 후:** 등록자에게 수락/거절 알림이 발송됩니다.
                    요청은 **5분 후 자동 만료**됩니다. (스케줄러 처리)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "연장 요청 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "신청자만 연장 요청 가능 (VERIFY_017)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_017",
                                      "message": "연장 요청은 신청자만 가능합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 진행 중인 연장 요청 있음(VERIFY_013) / 이미 연장 완료(VERIFY_011)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_013",
                                      "message": "이미 진행 중인 연장 요청이 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "약속 시간 5분 전 이후 요청 불가(VERIFY_010) / MATCHED 상태 아님(VERIFY_012)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_422_NOT_VERIFICATION_TIME))
            )
    })
    @PostMapping("/matches/{matchId}/extension/request")
    public ResponseEntity<ApiResponseDto<CreateMeetExtensionResponseDto>> createMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(meetVerificationCommandService.createMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 수락
    @Operation(
            summary = "만남 시간 연장 수락 (등록자 전용)",
            description = """
                    등록자가 신청자의 연장 요청을 수락합니다.
                    
                    **수락 시:**
                    - 약속 시간이 15분 연장됩니다.
                    - QR 토큰 TTL이 재설정됩니다. (이미 발급된 경우)
                    - GPS 인증 가능 시간도 함께 연장됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연장 수락 성공 - 약속 시간 15분 연장"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "등록자만 수락 가능(VERIFY_018) / 본인 요청은 본인이 응답 불가(VERIFY_014)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_018",
                                      "message": "연장 수락/거절은 등록자만 가능합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "연장 요청 만료 (VERIFY_016)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_016",
                                      "message": "연장 요청이 만료되었습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "응답 가능한 연장 요청 없음 (VERIFY_015)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_015",
                                      "message": "응답 가능한 연장 요청이 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PatchMapping("/matches/{matchId}/extension/accept")
    public ResponseEntity<ApiResponseDto<AcceptMeetExtensionResponseDto>> acceptMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationCommandService.acceptMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 거절
    @Operation(
            summary = "만남 시간 연장 거절 (등록자 전용)",
            description = """
                    등록자가 신청자의 연장 요청을 거절합니다.
                    - 거절 시 신청자에게 거절 알림이 발송됩니다.
                    - 이후 연장 재요청은 불가합니다. (1회 제한)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연장 거절 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "등록자만 거절 가능 (VERIFY_018)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_018",
                                      "message": "연장 수락/거절은 등록자만 가능합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 거절된 연장 요청 (VERIFY_019)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "VERIFY_019",
                                      "message": "이미 거절된 연장 요청입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PatchMapping("/matches/{matchId}/extension/reject")
    public ResponseEntity<ApiResponseDto<RejectMeetExtensionResponseDto>> rejectMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationCommandService.rejectMeetExtension(userId, matchId)));
    }

    // 만남 시간 연장 상태 조회
    @Operation(
            summary = "만남 시간 연장 상태 조회",
            description = """
                    현재 매칭의 연장 요청 상태를 조회합니다.
                    
                    **연장 상태값:**
                    - `NONE` : 연장 요청 없음
                    - `REQUESTED` : 연장 요청 중 (5분 내 응답 필요)
                    - `ACCEPTED` : 연장 수락됨
                    - `REJECTED` : 연장 거절됨
                    - `EXPIRED` : 연장 요청 만료 (5분 초과)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
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
    @GetMapping("/matches/{matchId}/extension")
    public ResponseEntity<ApiResponseDto<GetMeetExtensionResponseDto>> getMeetExtension(
            @PathVariable Long matchId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(ApiResponseDto.success(meetVerificationQueryService.getMeetExtension(userId, matchId)));
    }
}
