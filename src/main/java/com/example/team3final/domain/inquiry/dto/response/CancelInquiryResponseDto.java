package com.example.team3final.domain.inquiry.dto.response;

import com.example.team3final.domain.inquiry.entity.Inquiry;

import java.time.LocalDateTime;

public record CancelInquiryResponseDto (
        Long inquiryId,            // 취소된 문의 ID
        LocalDateTime cancelledAt  // 취소 시각
) {
    public static CancelInquiryResponseDto from(Inquiry inquiry) {
        return new CancelInquiryResponseDto(
                inquiry.getId(),
                inquiry.getCancelledAt()
        );
    }
}
