package com.example.team3final.domain.inquiry.repository;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 특정 유저의 문의 목록을 최신순으로 페이징 조회
    Page<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 문의 목록 조회
    // status, type 필터링, null이면 해당 조건을 무시하고 전체조회,
    // null이 아니면 해당 값으로 필터링
    @Query("""
             SELECT i
             FROM Inquiry i
             WHERE (:status IS NULL OR i.answerStatus = :status)
             AND (:type IS NULL OR i.inquiryType = :type)
             ORDER BY i.createdAt DESC
            """)
    Page<Inquiry> findAllByStatusAndType(
            @Param("status") InquiryAnswerStatus status,
            @Param("type") InquiryType type,
            Pageable pageable);
}
