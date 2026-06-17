package com.example.team3final.domain.match.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CancelMatchResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchCommandService;
import com.example.team3final.domain.match.service.MatchQueryService;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Match", description = "매칭 API - 선착순 매칭 신청, 조회, 취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MatchController {

    private final MatchCommandService matchCommandService;
    private final MatchQueryService matchQueryService;
    private final MeetVerificationInternalService meetVerificationInternalService;
    // 공통 에러 응답 예시 상수
    // 여러 API에서 동일한 에러 응답이 반복되므로 상수로 분리하여 중복 제거

    // 401: 인증 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 403: 매칭 당사자가 아님
    private static final String EXAMPLE_403_NOT_PARTICIPANT = """
            {
              "success": false,
              "code": "MATCH_002",
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

    // 404: 게시글 없음
    private static final String EXAMPLE_404_POST = """
            {
              "success": false,
              "code": "POST_001",
              "message": "존재하지 않는 게시글입니다.",
              "data": null
            }
            """;

    /**
     * 매칭 신청 (선착순 매칭 생성)
     */
    @Operation(
            summary = "매칭 신청",
            description = """
                    게시글에 선착순으로 매칭을 신청합니다.
                    - 신청 즉시 게시글 등록자와 동일한 포인트가 예치됩니다.
                    - 매칭 확정 시 게시글 정원에 맞는 채팅방이 자동 생성됩니다.
                    - Redis 분산락으로 동시 신청 중복을 방지합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "매칭 신청 성공 - 채팅방 자동 생성됨"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다시 만나고 싶지 않아요 관계의 게시글 신청 불가 (MATCH_009)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "MATCH_009",
                                      "message": "다시 만나고 싶지 않아요 관계의 게시글에는 신청할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_POST))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 매칭된 게시글 (MATCH_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "MATCH_004",
                                      "message": "이미 매칭된 게시글입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "본인 게시글 신청 불가(MATCH_003) / 모집 종료(MATCH_005) / 포인트 부족(POINT_002) / 중복 신청(MATCH_008)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "MATCH_003",
                                      "message": "본인 게시글에는 신청할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/posts/{postId}/matches")
    public ResponseEntity<ApiResponseDto<CreateMatchResponseDto>> createMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long postId
    ) {
        // 토큰에서 추출된 검증된 userId
        Long applicantId = userDetails.getUserId();

        CreateMatchResponseDto response = matchCommandService.createMatch(postId, applicantId);
        meetVerificationInternalService.createPendingVerification(response.matchId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    /**
     * 매칭 상세 조회
     */
    @Operation(
            summary = "매칭 상세 조회",
            description = """
                    매칭 ID로 단건 상세 정보를 조회합니다.
                    - 매칭 당사자(등록자 또는 신청자)만 조회 가능합니다.
                    - 상태값: MATCHED(매칭완료) / COMPLETED(만남완료) / CANCELLED(취소) /
                      HOST_NO_SHOW(등록자노쇼) / GUEST_NO_SHOW(신청자노쇼) /
                      BOTH_NO_SHOW(양측노쇼) / DISPUTED(이의제기중)
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
    @GetMapping("/matches/{matchId}")
    public ResponseEntity<ApiResponseDto<GetMatchResponseDto>> getMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId
    ) {
        // JWT 토큰에서 검증된 userId 추출 (당사자 검증용)
        Long currentUserId = userDetails.getUserId();

        return ResponseEntity.ok(
                ApiResponseDto.success(matchQueryService.getMatch(matchId, currentUserId)));
    }

    /**
     * 내 매칭 목록 조회 (페이지네이션 + 상태 필터)
     */
    @Operation(
            summary = "내 매칭 목록 조회",
            description = """
                    로그인한 사용자의 매칭 목록을 페이지네이션으로 조회합니다.
                    - status 미입력 시 전체 상태 조회
                    - 최대 페이지 크기: 50 (초과 시 50으로 고정)
                    - 정렬: 매칭 생성일 내림차순(최신순)
                    - status 가능 값: MATCHED / COMPLETED / CANCELLED /
                      HOST_NO_SHOW / GUEST_NO_SHOW / BOTH_NO_SHOW / DISPUTED
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            )
    })
    @GetMapping("/matches/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetMatchesResponseDto>>> getMatches(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId();

        int safeSize = Math.min(size, 50);

        // 정렬은 Repository native query의 ORDER BY m.created_at DESC에서 처리합니다.
        Pageable pageable = PageRequest.of(
                page,
                safeSize
        );

        return ResponseEntity.ok(
                ApiResponseDto.success(matchQueryService.getMatches(userId, status, pageable)));
    }

    /**
     * 매칭 취소
     */
    @Operation(
            summary = "매칭 취소",
            description = """
                    진행 중인 매칭을 취소합니다.
                    - 취소자: 예치 포인트의 50%만 반환됩니다. (나머지 50% 몰수)
                    - 상대방: 예치 포인트 100% 반환됩니다.
                    - MATCHED 상태일 때만 취소 가능합니다.
                    - 약속 시간 이후에는 취소할 수 없습니다.
                    - 게시글 상태는 OPEN으로 복구되지 않고 CANCELLED로 종료됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공 - 취소자 예치 포인트 50% 반환"),
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
                    responseCode = "422",
                    description = "취소 불가 상태 (MATCH_006) / 약속 시간 이후 취소 불가 (MATCH_007)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "MATCH_006",
                                      "message": "현재 상태의 매칭은 취소할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PatchMapping("/matches/{matchId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelMatchResponseDto>> cancelMatch(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long matchId,
            @Valid @RequestBody CancelMatchRequestDto request
            ) {
        Long userId = userDetails.getUserId();

        return ResponseEntity.ok(
                ApiResponseDto.success(matchCommandService.cancelMatch(matchId, userId, request)));
    }
}
