package com.example.team3final.domain.pointTransaction.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointReferenceType;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 거래 서비스 단위 테스트")
class PointTransactionServiceTest {

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private UserInternalService userInternalService;

    @InjectMocks
    private PointTransactionServiceImpl pointTransactionService;

    @Test
    @DisplayName("거래 유형이 없으면 사용자의 전체 포인트 거래 내역을 조회한다")
    void getPointTransactions_shouldFindAllWhenTypeIsNull() {
        PageRequest pageable = PageRequest.of(0, 10);
        PointTransaction transaction = pointTransaction(1L, PointTransactionType.CHARGE);
        when(userInternalService.getUserIdByEmail("user@test.ac.kr")).thenReturn(1L);
        when(pointTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        PageResponseDto<PointTransactionResponseDto> result =
                pointTransactionService.getPointTransactions("user@test.ac.kr", null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).transactionId()).isEqualTo(1L);
        verify(pointTransactionRepository).findAllByUserIdOrderByCreatedAtDesc(1L, pageable);
    }

    @Test
    @DisplayName("거래 유형이 있으면 사용자의 해당 유형 포인트 거래 내역만 조회한다")
    void getPointTransactions_shouldFindByTypeWhenTypeExists() {
        PageRequest pageable = PageRequest.of(0, 10);
        PointTransaction transaction = pointTransaction(2L, PointTransactionType.REFUND);
        when(userInternalService.getUserIdByEmail("user@test.ac.kr")).thenReturn(1L);
        when(pointTransactionRepository.findAllByUserIdAndTransactionTypeOrderByCreatedAtDesc(1L, PointTransactionType.REFUND, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        PageResponseDto<PointTransactionResponseDto> result =
                pointTransactionService.getPointTransactions("user@test.ac.kr", PointTransactionType.REFUND, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).transactionType()).isEqualTo(PointTransactionType.REFUND);
        verify(pointTransactionRepository)
                .findAllByUserIdAndTransactionTypeOrderByCreatedAtDesc(1L, PointTransactionType.REFUND, pageable);
    }

    @Test
    @DisplayName("페이지 크기가 허용 범위를 벗어나면 서비스 예외를 던진다")
    void getPointTransactions_shouldThrowWhenPageSizeIsInvalid() {
        PageRequest pageable = PageRequest.of(0, 51);

        assertThatThrownBy(() -> pointTransactionService.getPointTransactions("user@test.ac.kr", null, pageable))
                .isInstanceOf(ServiceException.class);
    }

    private PointTransaction pointTransaction(Long transactionId, PointTransactionType transactionType) {
        PointTransaction transaction = PointTransaction.builder()
                .userId(1L)
                .matchId(10L)
                .referenceType(PointReferenceType.PAYMENT)
                .referenceId(20L)
                .amount(1000)
                .transactionType(transactionType)
                .balanceAfter(11000)
                .pointSource(PointSource.PAID)
                .description("포인트 거래")
                .build();
        ReflectionTestUtils.setField(transaction, "id", transactionId);
        return transaction;
    }
}
