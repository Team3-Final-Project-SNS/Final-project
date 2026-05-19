package com.example.team3final.domain.pointTransaction.service;


import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
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
    /*
     * ==================== Service to Service Rules ====================
     *
     * PointTransaction 도메인은 로그인 사용자의 userId가 필요합니다.
     * userId 조회는 User 도메인의 책임이므로 UserService를 통해 가져옵니다.
     *
     * 흐름:
     * PointTransactionServiceImpl
     *   -> UserService
     *      -> UserRepository
     *
     * ================================================================
     */
    private final UserService userService;

    @Override
    public PageResponseDto<PointTransactionResponseDto> getPointTransactions(
            String email,
            PointTransactionType type,
            Pageable pageable
    ) {
        // 페이지 요청 값이 올바른지 검증합니다.
        validatePageable(pageable);

        // User 도메인 Service를 통해 로그인 사용자의 userId를 조회합니다.
        Long userId = userService.getUserIdByEmail(email);

        // userId와 거래 타입 조건에 맞는 포인트 거래내역을 조회합니다.
        Page<PointTransaction> pointTransactions = getPointTransactionPage(userId, type, pageable);

        // 포인트 거래내역이 없더라도 목록 조회는 빈 페이지를 정상 응답으로 반환합니다.
        return PageResponseDto.from(
                pointTransactions.map(this::toPointTransactionResponseDto)
        );
    }

    private void validatePageable(Pageable pageable) {
        // page는 0 이상이어야 합니다.
        if (pageable.getPageNumber() < 0) {
            throw new ServiceException(ErrorCode.POINT_TRANSACTION_INVALID_PAGE);
        }

        // size는 1 이상 50 이하만 허용합니다.
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 50) {
            throw new ServiceException(ErrorCode.POINT_TRANSACTION_INVALID_PAGE);
        }
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
        // PointTransaction Entity를 API 응답 DTO로 변환합니다.
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
