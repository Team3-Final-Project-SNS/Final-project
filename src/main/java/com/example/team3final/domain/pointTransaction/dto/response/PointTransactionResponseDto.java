package com.example.team3final.domain.pointTransaction.dto.response;

import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PointTransactionResponseDto(
        Long transactionId,
        Long userId,
        Long matchId,
        int amount,
        PointTransactionType transactionType,
        int balanceAfter,
        String description,
        LocalDateTime createdAt
) {
}