package com.example.team3final.domain.pointTransaction.repository;

import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.enums.PointReferenceType;
import com.example.team3final.domain.pointTransaction.enums.PointSettlementReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Page<PointTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<PointTransaction> findAllByUserIdAndTransactionTypeOrderByCreatedAtDesc(
            Long userId,
            PointTransactionType transactionType,
            Pageable pageable
    );

    // 동시성 테스트 검증용: 특정 matchId + 타입 목록으로 건수 조회
    @Query("SELECT COUNT(pt) FROM PointTransaction pt WHERE pt.matchId = :matchId AND pt.transactionType IN :types")
    long countByMatchIdAndTransactionTypeIn(
            @Param("matchId") Long matchId,
            @Param("types") List<PointTransactionType> types
    );

    // 동일 책임비에 최종 정산 결과가 이미 기록됐는지 확인한다.
    boolean existsByUserIdAndReferenceTypeAndReferenceIdAndSettlementReason(
            Long userId,
            PointReferenceType referenceType,
            Long referenceId,
            PointSettlementReason settlementReason
    );

    Optional<PointTransaction> findFirstByUserIdAndReferenceTypeAndReferenceIdAndSettlementReasonOrderByCreatedAtDesc(
            Long userId,
            PointReferenceType referenceType,
            Long referenceId,
            PointSettlementReason settlementReason
    );

    // tearDown 정리용
    @Modifying
    @Query("DELETE FROM PointTransaction pt WHERE pt.matchId = :matchId")
    void deleteByMatchId(@Param("matchId") Long matchId);
}
