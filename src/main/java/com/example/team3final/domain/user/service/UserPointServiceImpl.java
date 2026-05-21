package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
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

        // 2. 잔액 차감 — User 엔티티 메서드가 잔액 부족 시 ServiceException 던짐
        user.deductPoint(amount);

        // 3. PointTransaction 기록 — amount는 음수(차감을 표현)
        saveTransaction(user.getId(), matchId, -amount, PointTransactionType.DEPOSIT,
                user.getPoint());
    }

    // 포인트 전액 환수
    @Override
    public void refundPoint(Long userId, int amount, Long matchId) {
        // 1. 유저 조회
        User user = getUserOrThrow(userId);

        // 2. 포인트 지급
        user.addPoint(amount);

        // 3. PointTransaction 기록
        saveTransaction(user.getId(), matchId, amount, PointTransactionType.REFUND,
                user.getPoint());
    }

    // ===== private 헬퍼 =====
    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
    }

    private void saveTransaction(Long userId, Long matchId, int amount,
                                 PointTransactionType type, int balanceAfter) {
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(userId)
                        .matchId(matchId)
                        .amount(amount)
                        .transactionType(type)
                        .balanceAfter(balanceAfter)
                        .build()
        );
    }

}
