package com.example.team3final.domain.pointTransaction.entity;


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
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "match_id",unique = true, updatable = false) //  매칭 ID, 회원가입 보너스처럼 매칭과 무관한 거래는 null입니다.
    private Long matchId;

    @Column(name = "amount", nullable = false) //  포인트 변동량
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30) // 거래 상태
    private PointTransactionType transactionType;

    @Column(name = "balance_after", nullable = false) // 거래 후 잔액
    private int balanceAfter;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false) //  생성일
    private LocalDateTime createdAt;

    @Builder
    private PointTransaction(
            Long userId,
            Long matchId,
            int amount,
            PointTransactionType transactionType,
            int balanceAfter,
            String description
    ) {
        this.userId = userId;
        this.matchId = matchId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    // Entity가 처음 저장되기 직전에 생성일을 자동으로 기록합니다.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
