package com.example.team3final.domain.pointTransaction.service;


import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import org.springframework.data.domain.Pageable;



public interface PointTransactionService {

    // 로그인한 사용자의 포인트 거래내역을 조회합니다.
    PageResponseDto<PointTransactionResponseDto> getPointTransactions(
            String email,
            PointTransactionType type,
            Pageable pageable
    );


}
