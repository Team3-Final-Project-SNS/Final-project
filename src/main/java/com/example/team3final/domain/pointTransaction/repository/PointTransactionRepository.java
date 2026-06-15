package com.example.team3final.domain.pointTransaction.repository;

import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


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

    // 등록자 책임비는 postId를 참조 ID로 사용한다.
    // Post 락 안에서 이 조회를 선행해 REFUND/PARTIAL_REFUND/PENALTY 중복 정산을 막는다.
    boolean existsByUserIdAndMatchIdAndTransactionTypeIn(
            Long userId,
            Long matchId,
            List<PointTransactionType> transactionTypes
    );

    // tearDown 정리용
    @Modifying
    @Query("DELETE FROM PointTransaction pt WHERE pt.matchId = :matchId")
    void deleteByMatchId(@Param("matchId") Long matchId);
}
