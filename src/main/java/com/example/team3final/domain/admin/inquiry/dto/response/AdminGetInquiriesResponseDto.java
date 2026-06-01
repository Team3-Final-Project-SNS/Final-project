package com.example.team3final.domain.admin.inquiry.dto.response;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record AdminGetInquiriesResponseDto(

        Long inquiryId,
        String userNickname,          // UserService.getUserNicknameMap()으로 조회
        String title,
        InquiryType type,
        InquiryAnswerStatus answerStatus,
        LocalDateTime createdAt
) {
    public static AdminGetInquiriesResponseDto of(Inquiry inquiry, String userNickname) {
        return new AdminGetInquiriesResponseDto(
                inquiry.getId(),
                userNickname,
                inquiry.getTitle(),
                inquiry.getInquiryType(),
                inquiry.getAnswerStatus(),
                inquiry.getCreatedAt()
        );
    }
}
