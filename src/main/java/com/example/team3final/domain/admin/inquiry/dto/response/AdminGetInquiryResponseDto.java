package com.example.team3final.domain.admin.inquiry.dto.response;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record AdminGetInquiryResponseDto(

        Long inquiryId,
        String userNickname,
        String userEmail,
        String universityName,
        String title,
        String content,
        InquiryType type,
        InquiryAnswerStatus answerStatus,
        AnswerDto answer,          // 내부 레코드 타입으로 변경
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // 내부 레코드로 이동
    public record AnswerDto(
            Long answerId,
            String adminName,
            String content,
            LocalDateTime createdAt
    ) {
        public static AnswerDto from(InquiryAnswer inquiryAnswer) {
            return new AnswerDto(
                    inquiryAnswer.getId(),
                    inquiryAnswer.getAdminName(),
                    inquiryAnswer.getContent(),
                    inquiryAnswer.getCreatedAt()
            );
        }
    }

    public static AdminGetInquiryResponseDto of(
            Inquiry inquiry,
            String userNickname,
            String userEmail,
            String universityName,
            InquiryAnswer answer
    ) {
        return new AdminGetInquiryResponseDto(
                inquiry.getId(),
                userNickname,
                userEmail,
                universityName,
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getInquiryType(),
                inquiry.getAnswerStatus(),
                answer != null ? AnswerDto.from(answer) : null,
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
