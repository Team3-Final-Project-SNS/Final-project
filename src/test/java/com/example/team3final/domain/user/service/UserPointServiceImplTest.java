package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PointTransactionException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPointServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @InjectMocks
    private UserPointServiceImpl userPointService;

    @Test
    @DisplayName("포인트 차감 - 성공")
    void deductPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.deduct(500)).willReturn(new User.DeductResult(500, 0));
        given(user.getTotalPoint()).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.deductPoint(1L, 500, 100L);

        // then
        verify(user).deduct(500);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("포인트 차감 - 무료 포인트와 유료 포인트를 각각 기록")
    void deductPoint_Success_FreeAndPaid() {
        // given
        User user = mock(User.class);
        given(user.deduct(500)).willReturn(new User.DeductResult(300, 200));
        given(user.getTotalPoint()).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.deductPoint(1L, 500, 100L);

        // then
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, times(2)).save(captor.capture());
        assertEquals(PointSource.FREE, captor.getAllValues().get(0).getPointSource());
        assertEquals(-300, captor.getAllValues().get(0).getAmount());
        assertEquals(PointSource.PAID, captor.getAllValues().get(1).getPointSource());
        assertEquals(-200, captor.getAllValues().get(1).getAmount());
    }

    @Test
    @DisplayName("포인트 전액 환급 - 성공")
    void refundPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(1000);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.refundPoint(1L, 500, 100L);

        // then
        verify(user).addFreePoint(500);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("포인트 50% 환급 - 성공")
    void partialRefundPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(750);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.partialRefundPoint(1L, 500, 100L);

        // then
        verify(user).addFreePoint(250);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("포인트 몰수 - 성공")
    void penaltyPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.penaltyPoint(1L, 500, 100L);

        // then
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("유료 포인트 충전 - 성공")
    void chargePoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(1000);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.chargePoint(1L, 1000, 10L);

        // then
        verify(user).addPaidPoint(1000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("유료 포인트 회수 - 성공")
    void withdrawChargedPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.withdrawPaid(1000)).willReturn(800);
        given(user.getTotalPoint()).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        int actual = userPointService.withdrawChargedPoint(1L, 1000, 10L);

        // then
        assertEquals(800, actual);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("신고 보상 포인트 지급 - 성공")
    void rewardReportPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(50);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.rewardReportPoint(1L, 50);

        // then
        verify(user).addFreePoint(50);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("후기 보상 포인트 지급 - 성공")
    void rewardReviewPoint_Success() {
        // given
        User user = mock(User.class);
        given(user.getTotalPoint()).willReturn(50);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.rewardReviewPoint(1L, 50, 100L);

        // then
        verify(user).addFreePoint(50);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("게시글 수정 예치금 차감 - 성공")
    void deductEditDeposit_Success() {
        // given
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.deduct(500)).willReturn(new User.DeductResult(300, 200));
        given(user.getTotalPoint()).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.deductEditDeposit(1L, 500);

        // then
        verify(user).deduct(500);
        verify(pointTransactionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("게시글 수정 예치금 환급 - 성공")
    void refundEditDeposit_Success() {
        // given
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getTotalPoint()).willReturn(500);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userPointService.refundEditDeposit(1L, 500);

        // then
        verify(user).addFreePoint(500);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("유저를 찾을 수 없을 때 예외 발생")
    void getUserOrThrow_Fail() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        PointTransactionException exception = assertThrows(PointTransactionException.class,
                () -> userPointService.deductPoint(1L, 500, 100L));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
