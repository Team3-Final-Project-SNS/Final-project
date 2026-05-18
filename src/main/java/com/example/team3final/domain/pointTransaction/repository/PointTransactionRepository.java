package com.example.team3final.domain.pointTransaction.repository;

import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Page<PointTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<PointTransaction> findAllByUserIdAndTransactionTypeOrderByCreatedAtDesc(
            Long userId,
            PointTransactionType transactionType,
            Pageable pageable
    );
}
