package com.example.team3final.domain.user.service;

import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 포인트 서비스 단위 테스트")
class UserPointServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @InjectMocks
    private UserPointServiceImpl userPointService;

    @Test
    @DisplayName("결제 충전은 유료 포인트를 증가시키고 충전 거래 내역을 저장한다")
    void chargePoint_shouldIncreasePaidPointAndSaveTransaction() {
        User user = user();
        when(userRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(user));
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);

        int balanceAfter = userPointService.chargePoint(1L, 3000, 10L);

        assertThat(balanceAfter).isEqualTo(3000);
        assertThat(user.getPaidPoint()).isEqualTo(3000);
        verify(pointTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(PointTransactionType.CHARGE);
        assertThat(captor.getValue().getPointSource()).isEqualTo(PointSource.PAID);
    }

    @Test
    @DisplayName("포인트 환불은 무료 포인트를 증가시키고 환불 거래 내역을 저장한다")
    void refundPoint_shouldIncreaseFreePointAndSaveTransaction() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);

        userPointService.refundPoint(1L, 1000, 20L);

        assertThat(user.getFreePoint()).isEqualTo(1000);
        verify(pointTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(PointTransactionType.REFUND);
    }

    @Test
    @DisplayName("예치 포인트 차감은 무료 포인트부터 차감하고 예치 거래 내역을 저장한다")
    void deductPoint_shouldDeductFreePointFirstAndSaveTransaction() {
        User user = user();
        user.addFreePoint(1000);
        when(userRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(user));
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);

        userPointService.deductPoint(1L, 500, 20L);

        assertThat(user.getFreePoint()).isEqualTo(500);
        verify(pointTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(-500);
        assertThat(captor.getValue().getTransactionType()).isEqualTo(PointTransactionType.DEPOSIT);
    }

    private User user() {
        return User.builder()
                .email("user@test.ac.kr")
                .password("password")
                .name("사용자")
                .nickname("tester")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
    }
}
