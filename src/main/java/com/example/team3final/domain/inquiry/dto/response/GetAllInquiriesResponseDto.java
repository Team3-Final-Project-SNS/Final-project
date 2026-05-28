package com.example.team3final.domain.inquiry.dto.response;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record GetAllInquiriesResponseDto(
        Long inquiryId,
        String title,
        InquiryType type,
        InquiryAnswerStatus answerStatus,
        LocalDateTime createdAt
) {
    public static GetAllInquiriesResponseDto from(Inquiry inquiry) {
        return new GetAllInquiriesResponseDto(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getInquiryType(),
                inquiry.getAnswerStatus(),
                inquiry.getCreatedAt()
        );
    }
}
