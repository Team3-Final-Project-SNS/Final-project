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
@Table(
        name = "point_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        // (user_id, match_id, transaction_type) 조합이 동일하면 중복 환불로 판단
                        // NULL 처리: match_id가 NULL인 경우(가입 보너스 등)는 UNIQUE 비교에서 NULL ≠ NULL이므로 제약 위반 아님
                        //            → 가입 보너스, 신고 보상 등 match_id=NULL 거래는 이 제약에 걸리지 않음 (의도된 동작)
                        // 왜 amount를 제약에 넣지 않는가:
                        //   동일 matchId에 같은 type으로 다른 금액이 들어오는 경우는 없음 (금액은 항상 예치금 기준으로 고정)
                        //   amount를 넣으면 "같은 금액 중복"만 막아서 방어력이 약해짐
                        name = "uk_point_tx_user_match_type",
                        columnNames = {"user_id", "match_id", "transaction_type"}
                )
        }
)
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
