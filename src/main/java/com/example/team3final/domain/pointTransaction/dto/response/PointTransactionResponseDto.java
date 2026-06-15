package com.example.team3final.domain.pointTransaction.dto.response;

import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.enums.PointReferenceType;
import com.example.team3final.domain.pointTransaction.enums.PointSettlementReason;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PointTransactionResponseDto(
        Long transactionId,
        Long userId,
        Long matchId,
        PointReferenceType referenceType,
        Long referenceId,
        PointSettlementReason settlementReason,
        int amount,
        PointTransactionType transactionType,
        int balanceAfter,
        String description,
        LocalDateTime createdAt
) {
}
