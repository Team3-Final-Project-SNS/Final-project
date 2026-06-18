package com.example.team3final.domain.payment.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PaymentException;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_merchant_uid",
                        columnNames = {"merchant_uid"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제를 요청한 유저 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 주문번호 — 우리가 채번(hankki_20260515_000001), PortOne paymentId 로 그대로 사용.
    // 한 번 정해지면 변경 불가(updatable=false), 고유(unique)
    @Column(name = "merchant_uid", nullable = false, updatable = false, length = 100)
    private String merchantUid;

    // 어떤 충전 패키지였는지 (이력/분석용). STRING 저장.
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_package", nullable = false, updatable = false, length = 20)
    private ChargePackage chargePackage;

    // 적립 포인트 스냅샷
    @Column(name = "charge_point", nullable = false, updatable = false)
    private int chargePoint;

    // 결제 금액(원). 포인트 충전 패턴이라 보통 chargePoint 와 1:1 (1P=1원).
    // 검증 시 PortOne 이 알려준 실제 금액과 이 값을 비교한다.
    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    // 결제 수단. 준비 시점에 기록.
    @Column(name = "pay_method", length = 30)
    private String payMethod;

    // 결제 상태 - STRING 저장
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus status;

    // 취소 사유 — cancelPayment() 시 저장
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // 실패 사유 — markFailed() 시 저장
    // ex) "PortOne 결제 미완료", "금액 불일치 (위변조 감지)", "PortOne API 호출 실패"
    @Column(name = "fail_reason", length = 255)
    private String failReason;

    // 결제 완료 시각 - 검증 통과 시점에 기록. 그 전엔 null
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 결제 취소 시각 - 취소 시점에 기록. 그 전엔 null
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * 생성 전용 빌더 — 결제 "준비(READY)" 시점에 호출.
     * status 는 항상 READY 로 시작하므로 내부에서 세팅.
     */
    @Builder
    private Payment(Long userId, String merchantUid, ChargePackage chargePackage, String payMethod) {
        this.userId = userId;
        this.merchantUid = merchantUid;
        this.chargePackage = chargePackage;
        this.chargePoint = chargePackage.getPoint(); // 패키지에서 적립 포인트 스냅샷
        this.amount = chargePackage.getAmount();     // 패키지에서 결제 금액 스냅샷
        this.payMethod = payMethod;
        this.status = PaymentStatus.READY;  // 준비 상태로 시작
    }

    // ===== 상태 전이 도메인 메서드 =====

    /**
     * 검증 통과 → 결제 완료(PAID).
     * @throws IllegalStateException READY 가 아닌 상태에서 호출 시 (잘못된 전이 차단)
     */
    public void markPaid() {
        if (this.status != PaymentStatus.READY) {
            // 현재 결제 상태에서는 요청한 상태 전이 불가
            throw new PaymentException(ErrorCode.PAY_INVALID_STATUS);
        }
        this.status = PaymentStatus.PAID;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 결제 취소(CANCELLED). PAID 상태에서만 가능.
     */
    public void markCancelled(String cancelReason) {
        if (this.status != PaymentStatus.PAID) {
            // 현재 결제 상태에서는 요청한 상태 전이 불가
            throw new PaymentException(ErrorCode.PAY_INVALID_STATUS);
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = cancelReason; // 취소 사유 기록
    }

    /**
     * 결제 실패(FAILED). READY 상태에서만 가능.
     */
    public void markFailed(String failReason) {
        if (this.status != PaymentStatus.READY) {
            // 현재 결제 상태에서는 요청한 상태 전이 불가
            throw new PaymentException(ErrorCode.PAY_INVALID_STATUS);
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason; // 실패 사유 기록
    }

    // ===== 조회 보조 메서드 =====

    /**
     * 이 결제의 소유자인지 — 취소 시 본인 검증(PAY_007)에 사용.
     */
    public boolean isOwner(Long userId) {
        return this.userId.equals(userId);
    }
}
