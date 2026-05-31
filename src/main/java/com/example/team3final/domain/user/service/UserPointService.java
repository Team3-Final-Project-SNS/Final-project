package com.example.team3final.domain.user.service;

public interface UserPointService {

    // 포인트 차감(예치)
    void deductPoint(Long userId, int amount, Long matchId);

    // 포인트 전액 환급
    void refundPoint(Long userId, int amount, Long matchId);

    // 포인트 50% 환급(매치 취소 패널티)
    void partialRefundPoint(Long userId, int amount, Long matchId);

    // 포인트 몰수 (노쇼 패널티)
    void penaltyPoint(Long userId, int amount, Long matchId);

    // 신고 채택 포상 / 후기 작성 포상 등 보상 포인트 지급
    // matchId가 없는 포상 -> null
    void rewardPoint(Long userId, int amount);

    // UserPointService 인터페이스에 추가
    void deductPointForEdit(Long userId, int amount, Long postId);  // EDIT_DEPOSIT 타입으로 차감
    void refundPointForEdit(Long userId, int amount, Long postId);  // EDIT_DEPOSIT 타입으로 환원

    /**
     * 유료 포인트 충전 — 결제 완료 시 호출.
     * @param userId    사용자
     * @param amount    충전된 포인트
     * @param paymentId 어떤 결제와 묶이는지 (PointTransaction.matchId 자리를 paymentId로 재활용 또는 별도 컬럼)
     */
    void chargePoint(Long userId, int amount, Long paymentId);

    /**
     * 유료 포인트 회수 — 결제 취소 시 호출.
     * 현재 paidPoint 잔액 한도 내에서 회수. 이미 사용된 만큼은 회수 불가.
     * @return 실제로 회수된 금액
     */
    int withdrawChargedPoint(Long userId, int amount, Long paymentId);
}
