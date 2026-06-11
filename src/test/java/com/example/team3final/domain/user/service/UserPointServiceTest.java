package com.example.team3final.domain.user.service;

import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPointServiceTest {

    @InjectMocks
    private UserPointServiceImpl userPointService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Test
    @DisplayName("포인트 환급 - 성공")
    void refundPoint_Success() {
        // given
        Long userId = 1L;
        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userPointService.refundPoint(userId, 1000, null);

        // then
        verify(user).addFreePoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("포인트 차감 - 성공")
    void deductPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(user));
        given(user.deduct(1000)).willReturn(new User.DeductResult(1000, 0));
        given(user.getTotalPoint()).willReturn(9000);

        userPointService.deductPoint(1L, 1000, 10L);

        verify(user).deduct(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("유료 포인트 충전 - 성공")
    void chargePoint_Success() {
        User user = mock(User.class);
        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(3000);

        int result = userPointService.chargePoint(1L, 3000, 100L);

        assertThat(result).isEqualTo(3000);
        verify(user).addPaidPoint(3000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("충전 포인트 회수 - 성공")
    void withdrawChargedPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(user));
        given(user.withdrawPaid(3000)).willReturn(2000);
        given(user.getTotalPoint()).willReturn(1000);

        int result = userPointService.withdrawChargedPoint(1L, 3000, 100L);

        assertThat(result).isEqualTo(2000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("부분 환불 - 성공")
    void partialRefundPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(5000);

        userPointService.partialRefundPoint(1L, 1000, 10L);

        verify(user).addFreePoint(500);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("패널티 기록 - 성공")
    void penaltyPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(5000);

        userPointService.penaltyPoint(1L, 1000, 10L);

        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("신고 보상 포인트 지급 - 성공")
    void rewardReportPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(6000);

        userPointService.rewardReportPoint(1L, 1000);

        verify(user).addFreePoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("리뷰 보상 포인트 지급 - 성공")
    void rewardReviewPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(6000);

        userPointService.rewardReviewPoint(1L, 1000, 10L);

        verify(user).addFreePoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("공통 보상 포인트 지급 - 성공")
    void rewardPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(6000);

        userPointService.rewardPoint(1L, 1000);

        verify(user).addFreePoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("수정 책임비 추가 차감 - 성공")
    void deductEditDeposit_Success() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(user));
        given(user.deduct(1000)).willReturn(new User.DeductResult(1000, 0));
        given(user.getTotalPoint()).willReturn(4000);

        userPointService.deductEditDeposit(1L, 1000);

        verify(user).deduct(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("수정 책임비 환불 - 성공")
    void refundEditDeposit_Success() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(6000);

        userPointService.refundEditDeposit(1L, 1000);

        verify(user).addFreePoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("총 포인트 조회 - 성공")
    void getTotalPoint_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getTotalPoint()).willReturn(5000);

        int result = userPointService.getTotalPoint(1L);

        assertThat(result).isEqualTo(5000);
    }
}
