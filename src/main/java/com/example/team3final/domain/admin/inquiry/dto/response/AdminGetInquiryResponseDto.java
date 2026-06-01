package com.example.team3final.domain.admin.inquiry.dto.response;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;

import java.time.LocalDateTime;

public record AdminGetInquiryResponseDto (

        Long inquiryId,
        String userNickname,      // 작성자 닉네임
        String userEmail,         // 작성자 이메일 (관리자 전용 필드)
        String universityName,    // 소속 대학교 이름 (관리자 전용 필드)
        String title,
        String content,
        InquiryType type,         // ACCOUNT / POINT / MATCH / REPORT / OTHER
        InquiryAnswerStatus answerStatus,  // PENDING / ANSWERED / ...
        AdminInquiryAnswerDto answer,      // 답변 없으면 null, 있으면 DTO
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminGetInquiryResponseDto of(
            Inquiry inquiry,
            String userNickname,
            String userEmail,
            String universityName,
            InquiryAnswer answer        // null 가능
    ) {
        return new AdminGetInquiryResponseDto(
                inquiry.getId(),
                userNickname,
                userEmail,
                universityName,
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getInquiryType(),         // enum 그대로 반환 (직렬화 시 STRING)
                inquiry.getAnswerStatus(),
                // answer가 null이면 null, 아니면 DTO로 변환
                answer != null ? AdminInquiryAnswerDto.from(answer) : null,
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()            // BaseUpdateEntity의 updatedAt
        );
    }
}
