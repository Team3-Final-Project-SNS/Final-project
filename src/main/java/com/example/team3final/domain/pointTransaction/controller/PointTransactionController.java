package com.example.team3final.domain.pointTransaction.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.service.PointTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PointTransaction", description = "포인트 거래 내역 API - 포인트 변동 이력 조회")
@RestController // REST API 요청을 처리하는 Controller입니다.
@RequiredArgsConstructor // final 필드를 생성자 주입 방식으로 주입합니다.
@RequestMapping("/api/v1/") // 포인트 거래 내역 API의 기본 URL입니다.
public class PointTransactionController {

    private final PointTransactionService pointTransactionService; // 포인트 거래 내역 비즈니스 로직을 처리합니다.

    // 공통 에러 응답 예시 상수

    // 401: 인증 토큰 없음 또는 만료
    private static final String EXAMPLE_401 = """
            {
              "success": false,
              "code": "AUTH_006",
              "message": "유효하지 않거나 만료된 토큰입니다.",
              "data": null
            }
            """;

    // 포인트 거래 내역 조회
    @Operation(
            summary = "내 포인트 거래 내역 조회",
            description = """
                    로그인한 사용자의 포인트 거래 내역을 최신순으로 조회합니다.
                    - type 미입력 시 전체 거래 내역 조회
                    - 최대 페이지 크기: 50
                    
                    **거래 유형 (type) 설명:**
                    - `JOIN_BONUS` : 회원가입 보너스 지급 (+10,000P)
                    - `CHARGE` : 유료 포인트 충전 (결제)
                    - `CHARGE_CANCELLED` : 유료 포인트 환불 (결제 취소)
                    - `DEPOSIT` : 책임비 포인트 예치 (게시글 작성 / 매칭 신청)
                    - `EDIT_DEPOSIT` : 책임비 포인트 변경 (게시글 수정)
                    - `REFUND` : 포인트 전액 반환 (정상 완료 / 상대방 취소)
                    - `PARTIAL_REFUND` : 포인트 일부 반환 (본인 매칭 취소 시 50% 반환)
                    - `PENALTY` : 패널티 포인트 차감 (노쇼 확정)
                    - `REPORT_REWARD` : 신고 채택 포상 (+50P)
                    - `REVIEW_REWARD` : 후기 작성 포상 (+50P)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 요청값 (POINT_001) - 페이지 번호 음수 또는 크기 1~50 외",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "POINT_001",
                                      "message": "페이지 요청 값이 올바르지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            )
    })
    @GetMapping("/me/points/transactions")
    public ResponseEntity<ApiResponseDto<PageResponseDto<PointTransactionResponseDto>>> getPointTransactions(
            Authentication authentication,
            @RequestParam(required = false) PointTransactionType type,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        // JWT 인증이 완료된 사용자의 식별값을 가져옵니다.
        String email = authentication.getName();

        // 포인트 거래내역 조회 결과를 공통 응답 포맷으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponseDto.success(pointTransactionService.getPointTransactions(email, type, pageable))
        );
    }
}