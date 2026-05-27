package com.example.team3final.domain.inquiry.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "inquiry_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryAnswer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원 문의 ID (1:1 관계)
    @Column(name = "inquiry_id", nullable = false, updatable = false, unique = true)
    private Long inquiryId;

    // 답변한 관리자 ID
    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    // 관리자 이름 (조회 편의용 비정규화)
    @Column(name = "admin_name", nullable = false, length = 50)
    private String adminName;

    // 답변 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private InquiryAnswer(Long inquiryId, Long adminId, String adminName, String content) {
        this.inquiryId = inquiryId;
        this.adminId = adminId;
        this.adminName = adminName;
        this.content = content;
    }
}
