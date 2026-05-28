package com.example.team3final.domain.inquiry.repository;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 특정 유저의 특정 시간 내 문의 개수 카운트
    @Query("""
        SELECT COUNT(i)
        FROM Inquiry i
        WHERE i.userId = :userId
        AND i.createdAt >= :start
        AND i.createdAt <= :end
        """)
    long countByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("start")LocalDateTime start,
            @Param("end") LocalDateTime end
            );
}
