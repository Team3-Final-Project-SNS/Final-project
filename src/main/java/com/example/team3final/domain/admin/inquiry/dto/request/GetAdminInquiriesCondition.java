package com.example.team3final.domain.admin.inquiry.dto.request;

import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter // @ModelAttribute 바인딩을 위해서 필요
@NoArgsConstructor
public class GetAdminInquiriesCondition {

    // 값 없으면 -> null -> 전체 조회
    private InquiryAnswerStatus status;

    // 값 없으면 null -> 전체 조회
    private InquiryType type;
}
