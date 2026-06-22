package com.example.team3final.domain.payment.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CancelPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.GetPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;
import com.example.team3final.domain.payment.service.PaymentCommandService;
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

@Tag(name = "Payment", description = "결제 API - 포인트 충전 결제 준비, 검증, 내역 조회, 취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentCommandService paymentCommandService;

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

    // 403: 본인 결제 건이 아님
    private static final String EXAMPLE_403_NOT_OWNER = """
            {
              "success": false,
              "code": "PAY_006",
              "message": "본인의 결제 건만 취소할 수 있습니다.",
              "data": null
            }
            """;

    // 404: 결제 건 없음
    private static final String EXAMPLE_404_PAYMENT = """
            {
              "success": false,
              "code": "PAY_002",
              "message": "존재하지 않는 결제 건입니다.",
              "data": null
            }
            """;

    // 409: 이미 처리된 결제 건
    private static final String EXAMPLE_409_ALREADY_PROCESSED = """
            {
              "success": false,
              "code": "PAY_004",
              "message": "이미 처리된 결제 건입니다.",
              "data": null
            }
            """;

    /**
     * 결제 준비 — POST /api/v1/payments
     * 프론트 흐름:
     * 1. 이 API 호출 → merchantUid + paymentId 받음
     * 2. PortOne SDK에 merchantUid 전달해서 실제 결제 진행
     * 3. 결제 완료 후 verifyPayment API 호출
     */
    @Operation(
            summary = "결제 준비",
            description = """
                    PortOne 결제 전 서버에서 주문 ID(merchantUid)를 생성하고 결제 준비 상태(READY)를 DB에 기록합니다.
                    
                    **결제 흐름:**
                    1. 이 API 호출 → `merchantUid` + `paymentId` 수신
                    2. 프론트에서 PortOne SDK에 `merchantUid` 전달 → 실제 결제 진행
                    3. 결제 완료 후 `/payments/{paymentId}/verify` API 호출
                    
                    **충전 가능 패키지:** 3,000P / 5,000P / 10,000P / 20,000P
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "결제 준비 성공 - merchantUid 발급됨"),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 충전 패키지 (PAY_001) - 3000/5000/10000/20000P 중 하나여야 함",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PAY_001",
                                      "message": "최소 충전 금액은 1,000P 입니다.",
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
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreatePaymentResponseDto>> createPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreatePaymentRequestDto request
    ) {
        Long userId = userDetails.getUserId();
        CreatePaymentResponseDto response = paymentCommandService.createPayment(userId, request);

        // 201 Created - 새로운 결제 준비 건이 생성됐으므로
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    // 결제 완료 검증
    @Operation(
            summary = "결제 완료 검증",
            description = """
                    PortOne SDK 결제 완료 후 서버에서 결제 금액을 검증하고 포인트를 지급합니다.
                    
                    **검증 절차:**
                    1. DB의 결제 금액 vs PortOne 실제 결제 금액 비교 (위변조 방지)
                    2. PortOne API 호출로 결제 상태 확인
                    3. 검증 성공 시 포인트 지급 + PAID 상태 전환
                    
                    **보안:** 금액 불일치 시 위변조로 판단하고 결제를 FAILED 처리합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 성공 - 포인트 지급 완료"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "결제 건을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_PAYMENT))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 결제 건 (PAY_004)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_409_ALREADY_PROCESSED))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "금액 불일치 위변조 감지(PAY_003) / PortOne 검증 실패(PAY_005)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PAY_003",
                                      "message": "결제 금액이 일치하지 않습니다. (위변조 감지)",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/{paymentId}/verify")
    public ResponseEntity<ApiResponseDto<VerifyPaymentResponseDto>> verifyPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long paymentId,
            @Valid @RequestBody VerifyPaymentRequestDto request
    ) {
       Long userId = userDetails.getUserId();
       return ResponseEntity.ok(
               ApiResponseDto.success(
                       paymentCommandService.verifyPayment(userId, paymentId, request)
               )
       );
    }

    // 결제 내역 조회
    @Operation(
            summary = "내 결제 내역 조회",
            description = """
                    로그인한 사용자의 결제 내역을 최신순으로 조회합니다.
                    - 최대 페이지 크기: 50
                    - 결제 상태: READY(준비) / PAID(완료) / CANCELLED(취소) / FAILED(실패)
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
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetPaymentResponseDto>>> getPayments(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId();
        // size 최대 50 제한
        Pageable pageable = PageRequest.of(page, Math.min(size,50));
        return ResponseEntity.ok(
                ApiResponseDto.success(
                        paymentCommandService.getPayments(userId, pageable)
                )
        );
    }

    // 결제 취소
    @Operation(
            summary = "결제 취소",
            description = """
                    완료된 결제를 취소하고 포인트를 회수합니다.
                    
                    **취소 정책:**
                    - PAID 상태인 결제만 취소 가능합니다.
                    - 이미 포인트를 사용한 경우 취소가 불가합니다.
                    - PortOne을 통해 실제 결제 취소(환불)가 진행됩니다.
                    - 환불은 1,000원 단위로만 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공 - 포인트 회수 및 환불 처리됨"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 결제 건이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_OWNER))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "결제 건을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_PAYMENT))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 결제 건 (PAY_004)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_409_ALREADY_PROCESSED))
            )
    })
    @PatchMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelPaymentResponseDto>> cancelPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long paymentId
    ) {
        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(
                ApiResponseDto.success(
                        paymentCommandService.cancelPayment(userId, paymentId)
                )
        );
    }

    // 결제 실패 처리
    // 프론트가 결제창 취소 또는 실패 시 즉시 호출
    @Operation(
            summary = "결제 실패 처리",
            description = """
                    프론트에서 결제창 취소 또는 실패 시 즉시 호출합니다.
                    - READY 상태인 결제를 FAILED로 전환합니다.
                    - 이미 PAID/CANCELLED/FAILED 상태면 무시합니다. (멱등성 보장)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실패 처리 성공 (또는 이미 처리된 건은 무시)"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 결제 건이 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_NOT_OWNER))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "결제 건을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_PAYMENT))
            )
    })
    @PatchMapping("/{paymentId}/fail")
    public ResponseEntity<ApiResponseDto<Void>> failPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long paymentId
    ) {
        Long userId = userDetails.getUserId();
        paymentCommandService.failPayment(userId, paymentId);

        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
