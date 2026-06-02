package com.example.team3final.domain.pointTransaction.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PointTransactionServiceImplTest {

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private PointTransactionServiceImpl pointTransactionService;

    @Test
    @DisplayName("포인트 거래 내역 조회 - 성공")
    void getPointTransactions_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        given(userService.getUserIdByEmail(anyString())).willReturn(1L);

        PointTransaction transaction = mock(PointTransaction.class);
        given(transaction.getId()).willReturn(100L);
        given(transaction.getUserId()).willReturn(1L);

        Page<PointTransaction> page = new PageImpl<>(List.of(transaction));
        given(pointTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .willReturn(page);

        // when
        PageResponseDto<PointTransactionResponseDto> result = pointTransactionService.getPointTransactions("test@test.com", null, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(100L, result.content().get(0).transactionId());
    }
}
