package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PointTransactionException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserPointServiceImpl implements UserPointService{

    private final UserRepository userRepository;
    private final PointTransactionRepository pointTransactionRepository;

    // 포인트 차감(예치)
    @Override
    public void deductPoint(Long userId, int amount, Long matchId) {
        // 1. 유저 조회
        User user = getUserOrThrow(userId);

        // user.deduct(amount) 가 무료/유료 분해 결과를 반환
        User.DeductResult result = user.deduct(amount);

        // PointTransaction 기록 — 무료에서 빠진 분과 유료에서 빠진 분을 분리해 두 건 기록
        // (한 건으로 합쳐도 되지만, 분리하면 통계/분쟁 추적이 명확)
        if (result.fromFree() > 0) {
            saveTransaction(userId, matchId, -result.fromFree(),
                    PointTransactionType.DEPOSIT, user.getTotalPoint(),
                    PointSource.FREE);  // ↓ PointTransaction에 source 컬럼 추가 권장
        }
        if (result.fromPaid() > 0) {
            saveTransaction(userId, matchId, -result.fromPaid(),
                    PointTransactionType.DEPOSIT, user.getTotalPoint(),
                    PointSource.PAID);
        }
    }

    // 포인트 전액 환급
    @Override
    public void refundPoint(Long userId, int amount, Long matchId) {
        // 1. 유저 조회
        User user = getUserOrThrow(userId);

        // 2. 포인트 지급
        user.addPaidPoint(amount);

        // 3. PointTransaction 기록
        saveTransaction(userId, matchId, amount,
                PointTransactionType.REFUND, user.getTotalPoint(), PointSource.PAID);
    }

    // 결제 충전 — paid_point로
    @Override
    public void chargePoint(Long userId, int amount, Long paymentId) {
        User user = getUserOrThrow(userId);
        user.addPaidPoint(amount);
        // paymentId를 matchId 자리에 넣을지, PointTransaction 스키마에 paymentId 컬럼을 추가할지 결정
        saveTransaction(userId, paymentId, amount,
                PointTransactionType.CHARGE, user.getTotalPoint(), PointSource.PAID);
    }

    // 결제 취소 시 회수
    @Override
    public int withdrawChargedPoint(Long userId, int amount, Long paymentId) {
        User user = getUserOrThrow(userId);
        int actual = user.withdrawPaid(amount);
        if (actual > 0) {
            saveTransaction(userId, paymentId, -actual,
                    PointTransactionType.CHARGE_CANCELLED, user.getTotalPoint(), PointSource.PAID);
        }
        return actual;
    }

    // 포인트 50% 환급 (매칭 취소 패널티)
    @Override
    public void partialRefundPoint(Long userId, int amount, Long matchId) {
        // 1. 유저 조회
        User user = getUserOrThrow(userId);

        // 2. 50% 계산
        int refundAmount = amount / 2;

        // 3. 포인트 지급
        user.addPaidPoint(refundAmount);

        // 4. PointTransaction 기록 — PARTIAL_REFUND 타입
        saveTransaction(userId, matchId, refundAmount, PointTransactionType.PARTIAL_REFUND,
                user.getTotalPoint(), PointSource.FREE);
    }

    // 포인트 몰수 (노쇼 패널티)
    @Override
    public void penaltyPoint(Long userId, int amount, Long matchId) {
        // 1. 유저 조회 (존재 확인용)
        User user = getUserOrThrow(userId);

        // 2. PointTransaction 기록 — PENALTY 타입, amount는 음수
        //    user.point는 이미 예치 시점에 차감됐으므로 변경 없음
        saveTransaction(userId, matchId, -amount, PointTransactionType.PENALTY,
                user.getTotalPoint(), PointSource.FREE);
    }

    // 포상 포인트 지급
    @Override
    public void rewardPoint(Long userId, int amount) {

        // 유저 조회
        User user = getUserOrThrow(userId);

        // 포인트 적립
        user.addFreePoint(amount);

        // PointTransaction 기록 -> REPORT_REWARD 적립, matchId는 신고에 없으므로 -> null
        saveTransaction(userId, null, amount,
                PointTransactionType.REPORT_REWARD, user.getTotalPoint(), PointSource.FREE);
    }

    @Override
    public void deductPointForEdit(Long userId, int amount, Long postId) {

    }

    @Override
    public void refundPointForEdit(Long userId, int amount, Long postId) {

    }

    // ===== private 헬퍼 =====
    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new PointTransactionException(ErrorCode.USER_NOT_FOUND));
    }

    private void saveTransaction(Long userId, Long matchId, int amount,
                                 PointTransactionType type, int balanceAfter,
                                 PointSource pointSource) {
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(userId)
                        .matchId(matchId)
                        .amount(amount)
                        .transactionType(type)
                        .balanceAfter(balanceAfter)
                        .pointSource(pointSource)
                        .build()
        );
    }

}
