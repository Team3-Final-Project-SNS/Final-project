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
import java.util.List;

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

    // 중복 카테고리 검증용 쿼리 -같은 유저가, 같은 문의 유형(카테고리)으로
    // 처리 중인 상태(PENDING 또는 IN_PROGRESS)인 문의가 존재하는지 확인
    @Query("""
        SELECT COUNT(i) > 0
        FROM Inquiry i
        WHERE i.userId = :userId
        AND i.inquiryType = :inquiryType
        AND i.answerStatus IN :statuses
        """)
    boolean existsByUserIdAndInquiryTypeAndAnswerStatusIn(
            @Param("userId") Long userId,
            @Param("inquiryType") InquiryType inquiryType,
            @Param("statuses") List<InquiryAnswerStatus> statuses
    );

    // 특정 유저의 문의 목록을 최신순으로 페이징 조회
    Page<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

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
