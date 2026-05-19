package com.example.team3final.domain.pointTransaction.controller;


import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.service.PointTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController // REST API 요청을 처리하는 Controller입니다.
@RequiredArgsConstructor // final 필드를 생성자 주입 방식으로 주입합니다.
@RequestMapping("/api/v1/") // 포인트 거래 내역 API의 기본 URL입니다.
public class PointTransactionController {

    private final PointTransactionService pointTransactionService; // 포인트 거래 내역 비즈니스 로직을 처리합니다.

    // 포인트 거래 내역 조회
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
