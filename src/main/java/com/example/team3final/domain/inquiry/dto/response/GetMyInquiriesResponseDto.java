package com.example.team3final.domain.inquiry.dto.response;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record GetMyInquiriesResponseDto (
        Long inquiryId,
        String title,
        InquiryType type,
        InquiryAnswerStatus answerStatus,
        LocalDateTime createdAt
) {
    public static GetMyInquiriesResponseDto from(Inquiry inquiry) {
        return new GetMyInquiriesResponseDto(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getInquiryType(),
                inquiry.getAnswerStatus(),
                inquiry.getCreatedAt()
        );
    }
}
