package com.example.team3final.domain.inquiry.dto.response;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;

import java.time.LocalDateTime;

public record CreateInquiryResponseDto (
        Long inquiryId,
        InquiryAnswerStatus status,
        LocalDateTime createdAt
) {
    public static CreateInquiryResponseDto from(Inquiry inquiry) {
        return new CreateInquiryResponseDto(
                inquiry.getId(),
                inquiry.getAnswerStatus(),
                inquiry.getCreatedAt()
        );
    }
}
