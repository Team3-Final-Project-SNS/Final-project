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
}
