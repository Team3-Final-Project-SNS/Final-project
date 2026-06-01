package com.example.team3final.domain.pointTransaction.entity;


import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "point_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "match_id", nullable = true, updatable = false)
    private Long matchId;

    @Column(name = "amount", nullable = false) //  포인트 변동량
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30) // 거래 상태
    private PointTransactionType transactionType;

    @Column(name = "balance_after", nullable = false) // 거래 후 잔액
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_source", nullable = false, length = 10)
    private PointSource pointSource;

    @Column(name = "description")
    private String description;


    @Builder
    private PointTransaction(
            Long userId,
            Long matchId,
            int amount,
            PointTransactionType transactionType,
            int balanceAfter,
            PointSource pointSource,
            String description
    ) {
        this.userId = userId;
        this.matchId = matchId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.balanceAfter = balanceAfter;
        this.pointSource = pointSource;
        this.description = description;
    }
}
