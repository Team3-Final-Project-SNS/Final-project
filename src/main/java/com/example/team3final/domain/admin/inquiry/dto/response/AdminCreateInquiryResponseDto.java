package com.example.team3final.domain.admin.inquiry.dto.response;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;

import java.time.LocalDateTime;

public record AdminCreateInquiryResponseDto (

        Long answerId,              // 생성된 답변 ID
        Long inquiryId,             // 원 문의 ID
        String adminName,           // 답변한 관리자 이름 (비정규화 저장값)
        String content,             // 답변 내용
        LocalDateTime createdAt     // 답변 작성 시각
) {
    public static AdminCreateInquiryResponseDto from(InquiryAnswer inquiryAnswer) {
        return new AdminCreateInquiryResponseDto(
                inquiryAnswer.getId(),
                inquiryAnswer.getInquiryId(),
                inquiryAnswer.getAdminName(),
                inquiryAnswer.getContent(),
                inquiryAnswer.getCreatedAt()
        );
    }
}
