package com.example.team3final.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryAnswerStatus {

    PENDING("대기"),
    IN_PROGRESS("처리중"),
    ANSWERED("답변완료"),
    CLOSED("종결"),
    WITHDRAWN("취소");

    private final String description;
}
