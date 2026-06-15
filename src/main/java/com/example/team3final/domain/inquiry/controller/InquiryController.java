package com.example.team3final.domain.inquiry.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.service.InquiryCommandService;
import com.example.team3final.domain.inquiry.service.InquiryQueryService;
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

@Tag(name = "Inquiry", description = "고객 문의 API - 문의 접수, 조회, 취소")
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryCommandService inquiryCommandService;
    private final InquiryQueryService inquiryQueryService;

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

    // 403: 본인 문의 아님
    private static final String EXAMPLE_403_ACCESS_DENIED = """
            {
              "success": false,
              "code": "INQUIRY_003",
              "message": "본인의 문의만 조회할 수 있습니다.",
              "data": null
            }
            """;

    // 404: 문의 없음
    private static final String EXAMPLE_404_INQUIRY = """
            {
              "success": false,
              "code": "INQUIRY_001",
              "message": "존재하지 않는 문의입니다.",
              "data": null
            }
            """;

    // 고객 문의 접수
    @Operation(
            summary = "고객 문의 접수",
            description = """
                    고객 문의를 접수합니다.
                    
                    **문의 카테고리(type):**
                    - `ACCOUNT` : 계정, 인증, 로그인 관련
                    - `PAYMENT` : 결제, 포인트, 환불 관련
                    - `USAGE` : 이용 방법, 기능 사용 관련
                    - `HISTORY` : 이용 내역, 과거 매칭 관련
                    - `MATCH` : 매칭 오류, GPS/QR 인증 장애 관련
                    - `REPORT` : 신고, 노쇼, 불량 이용 관련
                    
                    **제한 정책:**
                    - 하루 최대 20회 접수 가능합니다.
                    - 직전 접수로부터 **1분 쿨다운**이 있습니다.
                    
                    **정지 계정(SUSPENDED) 제한:**
                    - `ACCOUNT` 카테고리 문의만 접수 가능합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "문의 접수 성공 - 관리자에게 접수 알림 발송"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "정지 계정 카테고리 제한 - ACCOUNT 카테고리만 접수 가능 (SUSPENDED_002)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "SUSPENDED_002",
                                      "message": "정지된 계정은 계정/인증 문의만 접수할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "하루 최대 접수 초과(INQUIRY_005) / 1분 쿨다운(INQUIRY_006)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "INQUIRY_006",
                                      "message": "문의 접수 후 1분 뒤에 다시 접수할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreateInquiryResponseDto>> createInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateInquiryRequestDto request
            ) {

        // jwt 토큰에서 검증된 userId 추출 (위변조 불가)
        Long userId = userDetails.getUserId();

        CreateInquiryResponseDto response = inquiryCommandService.createInquiry(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED).body(ApiResponseDto.success(response));
    }

    // 내 문의 상세(답변포함) 조회
    @Operation(
            summary = "내 문의 상세 조회",
            description = """
                    내가 접수한 문의의 상세 내용과 관리자 답변을 조회합니다.
                    
                    **답변 상태(answerStatus):**
                    - `PENDING` : 접수 완료, 답변 대기
                    - `READ` : 관리자가 문의를 확인함
                    - `ANSWERED` : 답변 완료
                    - `WITHDRAWN` : 사용자가 취소함
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
                    description = "본인 문의가 아님 (INQUIRY_003)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_ACCESS_DENIED))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음 (INQUIRY_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_INQUIRY))
            )
    })
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto<GetOneInquiryResponseDto>> getOneInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long inquiryId
    ) {
        Long userId = userDetails.getUserId();

        GetOneInquiryResponseDto response = inquiryQueryService.getOneInquiry(userId, inquiryId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 내 문의 목록 조회
    @Operation(
            summary = "내 문의 목록 조회",
            description = """
                    내가 접수한 문의 목록을 최신순으로 조회합니다.
                    - 취소된 문의(WITHDRAWN)도 포함됩니다.
                    - 최대 페이지 크기 제한 없음 (기본값: 20)
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
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetAllInquiriesResponseDto>>> getAllInquiries(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId(); // jwt 토큰에서 userId 추철

        // PageRequest.of(page, size): Pageable 구현체 생성
        Pageable pageable = PageRequest.of(page, size);

        PageResponseDto<GetAllInquiriesResponseDto> response = inquiryQueryService.getAllInquiries(userId, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 고객 문의 취소
    @Operation(
            summary = "고객 문의 취소",
            description = """
                    접수한 문의를 취소합니다.
                    
                    **취소 가능 상태:** `PENDING` (접수 대기) / `READ` (관리자 확인) 상태만 취소 가능합니다.
                    - `ANSWERED` (답변 완료) 상태는 취소 불가합니다.
                    - 이미 취소된(`WITHDRAWN`) 문의는 취소 불가합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰 없음 또는 만료",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_401))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 문의가 아님 (INQUIRY_003)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_403_ACCESS_DENIED))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음 (INQUIRY_001)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = EXAMPLE_404_INQUIRY))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "취소 불가 상태 - 답변 완료 또는 이미 취소됨 (INQUIRY_004)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "INQUIRY_004",
                                      "message": "처리가 시작된 문의는 취소할 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @PatchMapping("/{inquiryId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelInquiryResponseDto>> cancelInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long inquiryId
    ) {
        Long userId = userDetails.getUserId();

        CancelInquiryResponseDto response = inquiryCommandService.cancelInquiry(userId, inquiryId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
