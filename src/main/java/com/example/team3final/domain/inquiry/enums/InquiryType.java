package com.example.team3final.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryType {

    ACCOUNT("계정"),
    POINT("포인트"),
    MATCH("매칭"),
    REPORT("신고"),
    OTHER("기타");

    private final String description;
}
