package com.example.team3final.domain.admin.inquiry.dto.response;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;

import java.time.LocalDateTime;

public record AdminInquiryAnswerDto (

        Long answerId,       // InquiryAnswer PK
        String adminName,    // 답변한 관리자 이름 (비정규화 컬럼)
        String content,      // 답변 내용
        LocalDateTime createdAt  // 답변 작성 시각
) {
    public static AdminInquiryAnswerDto from(InquiryAnswer inquiryAnswer) {
        return new AdminInquiryAnswerDto(
                inquiryAnswer.getId(),
                inquiryAnswer.getAdminName(),
                inquiryAnswer.getContent(),
                inquiryAnswer.getCreatedAt()
        );
    }
}
