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
        user.addFreePoint(amount);

        // 3. PointTransaction 기록
        saveTransaction(userId, matchId, amount,
                PointTransactionType.REFUND, user.getTotalPoint(), PointSource.FREE);
    }

    // 결제 충전 — paid_point로
    @Override
    public int chargePoint(Long userId, int amount, Long paymentId) {
        User user = getUserOrThrow(userId);
        user.addPaidPoint(amount);
        // paymentId를 matchId 자리에 넣을지, PointTransaction 스키마에 paymentId 컬럼을 추가할지 결정
        saveTransaction(userId, paymentId, amount,
                PointTransactionType.CHARGE, user.getTotalPoint(), PointSource.PAID);
        // 충전 후 총 잔액 반환 — 호출 측에서 UserRepository 없이 잔액 조회 가능
        return user.getTotalPoint();
    }

    // 결제 취소 시 회수
    @Override
    public int withdrawChargedPoint(Long userId, int amount, Long paymentId) {
        User user = getUserOrThrow(userId);
        // withdrawPaid(): 현재 paidPoint 잔액 한도 내에서만 회수
        // 이미 책임비로 사용된 paidPoint는 회수 불가 → min(paidPoint, 요청금액)
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

        // 3. 환불은 freePoint로 지급하는 것이 정책 (PointSource.FREE와 일치)
        user.addFreePoint(refundAmount);

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

    // 신고 채택 포상 포인트 지급.
    // 이때 매칭이 Null인 이유: 신고는 매칭id가 중요하지 않음. 신고, 채팅, 유저 신고 등이 존재하기 때문.
    @Override
    public void rewardReportPoint(Long userId, int amount) {
        rewardPoint(
                userId,
                amount,
                null,
                PointTransactionType.REPORT_REWARD
        );
    }

   // 후기 작성 포인트
    @Override
    public void rewardReviewPoint(Long userId, int amount, Long matchId) {
        rewardPoint(
                userId,
                amount,
                matchId,
                PointTransactionType.REVIEW_REWARD
        );
    }

    // ===== private 헬퍼 =====

    // 보상 포인트 공통 처리 — freePoint로 지급
    private void rewardPoint(
            Long userId,
            int amount,
            Long matchId,
            PointTransactionType type
    ) {
        User user = getUserOrThrow(userId);

        // 포인트 적립
        user.addFreePoint(amount);

        // 호출한 보상 도메인에 맞는 거래 타입을 그대로 기록합니다.
        // 예: 신고 보상은 REPORT_REWARD, 후기 보상은 REVIEW_REWARD
        saveTransaction(userId, matchId, amount,
                type, user.getTotalPoint(), PointSource.FREE);
    }

    // matchId 없는 보상(신고 채택 등)에서 호출 — 내부적으로 private rewardPoint로 위임
    @Override
    public void rewardPoint(Long userId, int amount) {
        rewardPoint(userId, amount, null, PointTransactionType.REPORT_REWARD);
    }

    // 게시글 수정 시 책임비 차액 차감 — EDIT_DEPOSIT 타입으로 기록
    @Override
    public void deductEditDeposit(Long userId, int amount) {
        // 유저 조회
        User user = getUserOrThrow(userId);

        // 무료 먼저, 부족분은 유료 — 잔액 부족 시 예외
        User.DeductResult result = user.deduct(amount);

        // 무료에서 차감된 부분 기록
        if (result.fromFree() > 0) {
            saveTransaction(user.getId(), null, -result.fromFree(),
                    PointTransactionType.EDIT_DEPOSIT, user.getTotalPoint(), PointSource.FREE);
        }

        // 유료에서 차감된 부분 기록
        if (result.fromPaid() > 0) {
            saveTransaction(user.getId(), null, -result.fromPaid(),
                    PointTransactionType.EDIT_DEPOSIT, user.getTotalPoint(), PointSource.PAID);
        }
    }

    // 게시글 수정 시 책임비 차액 환불 — EDIT_DEPOSIT 타입으로 기록
    @Override
    public void refundEditDeposit(Long userId, int amount) {
        // 1. 유저 조회
        User user = getUserOrThrow(userId);

        // 2. 포인트 지급 — user.addPoint()가 내부에서 잔액에 더함
        user.addFreePoint(amount);

        // 3. PointTransaction 기록 — EDIT_DEPOSIT 타입, amount는 양수(지급 표현)
        //    matchId 없음: 게시글 수정은 매칭 시점이 아니므로 null
        saveTransaction(user.getId(), null, amount, PointTransactionType.EDIT_DEPOSIT,
                user.getTotalPoint(),PointSource.FREE);
    }

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
