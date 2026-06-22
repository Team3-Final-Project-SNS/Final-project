package com.example.team3final.domain.dispute.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.MyDisputeResponseDto;
import com.example.team3final.domain.dispute.service.DisputeCommandService;
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

import java.util.List;

@Tag(name = "Dispute", description = "이의제기 API - 노쇼 예정 상태에서 이의제기 제출, 조회, 재이의제기")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DisputeController {

    private final DisputeCommandService disputeCommandService;

    // 공통 에러 응답 예시 상수
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    private static final String EXAMPLE_403_NOT_PARTICIPANT = """
            {
              "success": false,
              "code": "MATCH_NOT_PARTICIPANT",
              "message": "해당 매칭의 당사자가 아닙니다.",
              "data": null
            }
            """;

    private static final String EXAMPLE_404_MATCH = """
            {
              "success": false,
              "code": "MATCH_001",
              "message": "존재하지 않는 매칭입니다.",
              "data": null
            }
            """;

    // ────────────────────────────────────────────────
    // 이의제기 접수
    // POST /api/v1/matches/{matchId}/disputes
    // ────────────────────────────────────────────────
    @Operation(
            summary = "이의제기 제출",
            description = """
                    노쇼 예정 상태에서 이의제기를 제출합니다.
                    
                    **제출 가능 조건:**
                    - 인증 상태가 노쇼 예정(`HOST_NO_SHOW` / `GUEST_NO_SHOW` / `BOTH_NO_SHOW`)이어야 합니다.
                    - 노쇼 판정 시각으로부터 **24시간 이내**에만 제출 가능합니다.
                    - 같은 매칭에 대해 **1회만** 제출 가능합니다.
                    
                    **제출 후:**
                    - 인증 상태 → `DISPUTE` (이의제기 진행 중)
                    - 관리자에게 이의제기 접수 알림 발송
                    - 관리자 검토 후 수용(ACCEPTED) 또는 기각(REJECTED) 판정
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "이의제기 제출 성공 - 관리자 검토 대기"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))),
            @ApiResponse(responseCode = "403", description = "매칭 당사자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_PARTICIPANT))),
            @ApiResponse(responseCode = "404", description = "매칭을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_MATCH))),
            @ApiResponse(responseCode = "409", description = "이미 이의제기 제출 (DISPUTE_003)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "DISPUTE_003",
                                      "message": "이미 이의제기를 제출했습니다.",
                                      "data": null
                                    }
                                    """))),
            @ApiResponse(responseCode = "422", description = "노쇼 예정 상태 아님(DISPUTE_001) / 24시간 초과(DISPUTE_002)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "DISPUTE_001",
                                      "message": "노쇼 예정 상태에서만 이의제기할 수 있습니다.",
                                      "data": null
                                    }
                                    """)))
    })
    @PostMapping("/matches/{matchId}/disputes")
    public ResponseEntity<ApiResponseDto<CreateDisputeResponseDto>> createDispute(
            // JWT 토큰에서 검증된 로그인 유저 정보를 주입받음 (클라이언트 전송값 신뢰 X)
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            // URL 경로의 {matchId} 값을 메서드 인자로 바인딩
            @PathVariable Long matchId,
            // 요청 본문 JSON → DTO 변환 + 유효성 검증(@NotBlank 등) 실행
            @Valid @RequestBody CreateDisputeRequestDto request
    ) {
        Long userId = userDetails.getUserId();

        CreateDisputeResponseDto response = disputeCommandService.createDispute(matchId, userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    // ────────────────────────────────────────────────
    // 내 이의제기 상세 조회
    // GET /api/v1/matches/{matchId}/disputes/me
    // ────────────────────────────────────────────────
    @Operation(
            summary = "내 이의제기 상세 조회",
            description = """
                    특정 매칭에 대해 내가 제출한 이의제기를 상세 조회합니다.
                    
                    **처리 상태(status) 정의:**
                    - `SUBMITTED` : 제출 완료, 관리자 검토 대기
                    - `UNDER_REVIEW` : 관리자 검토 중
                    - `ACCEPTED` : 수용 — 노쇼 취소, 예치 포인트 반환
                    - `REJECTED` : 기각 — 노쇼 확정, 예치 포인트 환수
                    - `HOLD` : 보류 — 추가 자료 요청, 24시간 내 재제출 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))),
            @ApiResponse(responseCode = "403", description = "매칭 당사자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_PARTICIPANT))),
            @ApiResponse(responseCode = "404", description = "매칭 없음(MATCH_001) / 제출한 이의제기 없음(DISPUTE_005)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "DISPUTE_005",
                                      "message": "제출한 이의제기가 없습니다.",
                                      "data": null
                                    }
                                    """)))
    })
    @GetMapping("/matches/{matchId}/disputes/me")
    public ResponseEntity<ApiResponseDto<DisputeResponseDto>> getDispute(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId
    ) {
        Long userId = userDetails.getUserId();

        DisputeResponseDto response = disputeCommandService.getDispute(matchId, userId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // ────────────────────────────────────────────────
    // 내 이의제기 전체 목록 조회
    // GET /api/v1/disputes/me
    // ────────────────────────────────────────────────
    @Operation(
            summary = "내 이의제기 목록 조회",
            description = """
                    내가 접수한 이의제기 전체 목록을 반환합니다.
                    
                    **반환 조건:**
                    - 로그인 유저가 제출한 모든 이의제기 (처리 상태 무관)
                    - 최신 제출순(내림차순) 정렬
                    - 접수한 이의제기가 없으면 빈 배열 반환
                    
                    **활용:**
                    - 고객센터 화면 우측 "노쇼 이의제기 접수 내역" 목록 표시용
                    - 목록 항목 클릭 시 `GET /api/v1/matches/{matchId}/disputes/me` 로 상세 조회
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열 반환)"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401)))
    })
    @GetMapping("/disputes/me")
    public ResponseEntity<ApiResponseDto<List<MyDisputeResponseDto>>> getMyDisputes(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails.getUserId();

        // 쓰기/읽기 모두 DisputeCommandService 에 위임
        List<MyDisputeResponseDto> response = disputeCommandService.getMyDisputes(userId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // ────────────────────────────────────────────────
    // 재이의제기 제출
    // POST /api/v1/matches/{matchId}/disputes/resubmit
    // ────────────────────────────────────────────────
    @Operation(
            summary = "재이의제기 제출",
            description = """
                    관리자가 HOLD 판정을 내린 경우 추가 자료를 첨부하여 재제출합니다.
                    
                    **제출 가능 조건:**
                    - 원본 이의제기 상태가 `HOLD`여야 합니다.
                    - HOLD 판정 시각으로부터 **24시간 이내**에만 재제출 가능합니다.
                    - 원본 이의제기와 **같은 유형(disputeType)**이어야 합니다.
                    - 같은 이의제기에 대해 **1회만** 재제출 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "재이의제기 제출 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))),
            @ApiResponse(responseCode = "403", description = "매칭 당사자가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_PARTICIPANT))),
            @ApiResponse(responseCode = "404", description = "HOLD 상태 이의제기 없음 (DISPUTE_006)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "DISPUTE_006",
                                      "message": "HOLD 상태인 이의제기가 없습니다.",
                                      "data": null
                                    }
                                    """))),
            @ApiResponse(responseCode = "422", description = "HOLD 아님(DISPUTE_007) / 유형 불일치(DISPUTE_008) / 24시간 초과(DISPUTE_009)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "DISPUTE_009",
                                      "message": "HOLD 판정 후 24시간이 초과되어 재신청이 불가합니다.",
                                      "data": null
                                    }
                                    """)))
    })
    @PostMapping("/matches/{matchId}/disputes/resubmit")
    public ResponseEntity<ApiResponseDto<CreateDisputeResponseDto>> reCreateDispute(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody CreateDisputeRequestDto request
    ) {
        Long userId = userDetails.getUserId();

        CreateDisputeResponseDto response = disputeCommandService.reCreateDispute(matchId, userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }
}