package com.example.team3final.domain.pointTransaction.service;


import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PointTransactionServiceImpl implements PointTransactionService {


    private final PointTransactionRepository pointTransactionRepository;
    private final UserService userService;

    @Override
    public PageResponseDto<PointTransactionResponseDto> getPointTransactions(
            String email,
            PointTransactionType type,
            Pageable pageable
    ) {
        // User 도메인에서 로그인 사용자의 userId를 조회합니다.
        Long userId = userService.getUserIdByEmail(email);

        // 거래 타입 조건에 맞는 포인트 거래내역을 조회합니다.
        Page<PointTransaction> pointTransactions = getPointTransactionPage(userId, type, pageable);

        // PointTransaction Entity를 Response DTO로 변환한 뒤 공통 페이지 응답으로 반환합니다.
        return PageResponseDto.from(
                pointTransactions.map(this::toPointTransactionResponseDto)
        );
    }

    private Page<PointTransaction> getPointTransactionPage(
            Long userId,
            PointTransactionType type,
            Pageable pageable
    ) {
        // type 값이 없으면 전체 거래내역을 최신순으로 조회합니다.
        if (type == null) {
            return pointTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        // type 값이 있으면 해당 거래 타입만 최신순으로 조회합니다.
        return pointTransactionRepository.findAllByUserIdAndTransactionTypeOrderByCreatedAtDesc(
                userId,
                type,
                pageable
        );
    }

    private PointTransactionResponseDto toPointTransactionResponseDto(PointTransaction pointTransaction) {
        // 포인트 거래 Entity를 API 응답 DTO로 변환합니다.
        return PointTransactionResponseDto.builder()
                .transactionId(pointTransaction.getId())
                .userId(pointTransaction.getUserId())
                .matchId(pointTransaction.getMatchId())
                .amount(pointTransaction.getAmount())
                .transactionType(pointTransaction.getTransactionType())
                .balanceAfter(pointTransaction.getBalanceAfter())
                .description(pointTransaction.getDescription())
                .createdAt(pointTransaction.getCreatedAt())
                .build();

    }
}
