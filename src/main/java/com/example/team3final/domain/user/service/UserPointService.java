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

    // 신고 채택 포상 포인트 지급
    void rewardReportPoint(Long userId, int amount);

    // 후기 작성 포상 포인트 지급
    void rewardReviewPoint(Long userId, int amount, Long matchId);
    // 신고 채택 포상 / 후기 작성 포상 등 보상 포인트 지급
    // matchId가 없는 포상 -> null
    void rewardPoint(Long userId, int amount);

    /**
     * 게시글 수정 시 책임비 차액 차감 — EDIT_DEPOSIT 타입으로 기록
     *
     * 사용처: PostServiceImpl.updatePost() — authorDeposit 증액 시
     *
     * deductPoint()와 동일한 차감 로직이지만 PointTransactionType이 다름
     *   - deductPoint → DEPOSIT  (게시글 최초 작성/매칭 신청 예치)
     *   - deductEditDeposit → EDIT_DEPOSIT  (수정으로 인한 추가 예치)
     * 거래 내역에서 "최초 예치"와 "수정 예치"를 구분할 수 있어야
     * 사용자 입장에서 포인트 변동 이력이 명확해짐
     *
     * @param userId  차감 대상 유저 ID
     * @param amount  차감할 금액 (양수, 내부에서 음수 변환하여 기록)
     */
    void deductEditDeposit(Long userId, int amount);

    /**
     * 게시글 수정 시 책임비 차액 환불 — EDIT_DEPOSIT 타입으로 기록
     *
     * 사용처: PostServiceImpl.updatePost() — authorDeposit 감액 시
     *
     * refundPoint()와 동일한 지급 로직이지만 PointTransactionType이 다름
     *   - refundPoint → REFUND  (게시글 삭제/만남 완료 후 전액 환불)
     *   - refundEditDeposit → EDIT_DEPOSIT  (수정으로 인한 차액 환불)
     *
     * @param userId  환불 대상 유저 ID
     * @param amount  환불할 금액 (양수)
     */
    void refundEditDeposit(Long userId, int amount);

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
