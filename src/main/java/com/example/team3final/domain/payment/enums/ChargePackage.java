package com.example.team3final.domain.payment.enums;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PaymentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ChargePackage {

    // (적립 포인트, 결제 금액원)
    P_3000(3000, 3000),
    P_5000(5000, 5000),
    P_10000(10000, 10000),
    P_20000(20000, 20000);

    private final int point;   // 충전 시 적립될 포인트
    private final int amount;  // 실제 결제할 금액(원)

    /**
     * 클라이언트가 보낸 충전 포인트 값으로 패키지를 찾는다.
     * - 정의된 패키지에 없는 값이면 PAY_001(최소/유효 충전 금액 위반)로 차단.
     * - 결제 준비(createPayment) 단계에서 요청 검증에 사용.
     *
     * @param point 클라이언트가 충전하려는 포인트
     * @throws PaymentException 정의되지 않은 패키지일 때
     */
    public static ChargePackage fromPoint(int point) {
        return Arrays.stream(values())
                .filter(p -> p.point == point)   // 적립 포인트가 일치하는 패키지 탐색
                .findFirst()
                .orElseThrow(() -> new PaymentException(ErrorCode.PAY_MIN_CHARGE));
    }
}
