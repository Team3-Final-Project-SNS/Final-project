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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PointTransactionServiceTest {

    @InjectMocks
    private PointTransactionServiceImpl pointTransactionService;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private UserService userService;

    @Test
    @DisplayName("포인트 거래 내역 조회 - 성공")
    void getPointTransactions_Success() {
        // given
        String email = "test@univ.ac.kr";
        Pageable pageable = PageRequest.of(0, 10);
        given(userService.getUserIdByEmail(email)).willReturn(1L);

        PointTransaction pt = PointTransaction.builder()
                .userId(1L)
                .amount(1000)
                .balanceAfter(5000)
                .build();
        Page<PointTransaction> page = new PageImpl<>(List.of(pt), pageable, 1);
        given(pointTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(anyLong(), any())).willReturn(page);

        // when
        PageResponseDto<PointTransactionResponseDto> result = pointTransactionService.getPointTransactions(email, null, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).amount()).isEqualTo(1000);
    }
}
