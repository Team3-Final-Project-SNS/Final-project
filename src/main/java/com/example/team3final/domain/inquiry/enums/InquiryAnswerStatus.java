package com.example.team3final.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryAnswerStatus {

    PENDING("접수완료"),
    READ("열람"),
    ANSWERED("답변완료"),
    WITHDRAWN("취소");

    private final String description;
}
