package com.example.team3final.domain.inquiry.dto.response;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record GetInquiryResponseDto(
        Long inquiryId,
        String title,
        String content,          // 상세 조회에서만 content 포함 (목록에는 없음)
        InquiryType type,
        InquiryAnswerStatus answerStatus,
        AnswerDto answer,        // 답변 정보 (미답변 시 null)
        LocalDateTime createdAt
) {
    // 중첩 record: 답변 정보만 따로 묶음
    public record AnswerDto(
            String adminName,        // 답변한 관리자 이름
            String content,          // 답변 내용
            LocalDateTime createdAt  // 답변 작성 시각
    ) {
        // InquiryAnswer 엔티티 → AnswerDto 변환
        public static AnswerDto from(InquiryAnswer answer) {
            return new AnswerDto(
                    answer.getAdminName(),
                    answer.getContent(),
                    answer.getCreatedAt() // BaseUpdateEntity의 createdAt
            );
        }
    }

    // Inquiry + InquiryAnswer(nullable)를 받아서 DTO 조립
    // answer가 null이면 AnswerDto도 null → 미답변 상태로 응답
    public static GetInquiryResponseDto of(Inquiry inquiry, InquiryAnswer answer) {
        return new GetInquiryResponseDto(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getInquiryType(),
                inquiry.getAnswerStatus(),
                answer != null ? AnswerDto.from(answer) : null, // 답변 없으면 null
                inquiry.getCreatedAt()
        );
    }
}
