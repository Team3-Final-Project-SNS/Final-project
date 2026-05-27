package com.example.team3final.domain.inquiry.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.common.entity.BaseUpdateEntity;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseUpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 20)
    private InquiryType inquiryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false, length = 20)
    private InquiryAnswerStatus answerStatus;

    // ERD에 별도 존재하는 취소일 컬럼 — BaseEntity의 deletedAt과 별개
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    private Inquiry(Long userId, String title, String content, InquiryType inquiryType) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.inquiryType = inquiryType;
        this.answerStatus = InquiryAnswerStatus.PENDING;
    }

    // 문의 취소
    public void withdraw() {
        this.answerStatus = InquiryAnswerStatus.WITHDRAWN;
        this.cancelledAt = LocalDateTime.now();
    }

    // 본인 문의인지 확인
    public boolean isOwner(Long userId) {
        return this.userId.equals(userId);
    }

    // PENDING 상태만 취소 가능
    public boolean isWithdrawable() {
        return this.answerStatus == InquiryAnswerStatus.PENDING;
    }
}
