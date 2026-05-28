package com.example.team3final.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryType {

    ACCOUNT("계정/인증"),
    PAYMENT("결제/포인트"),
    USAGE("이용방법/기능"),
    HISTORY("이용내역"),
    MATCH("매칭오류/장애"),
    REPORT("신고/불량이용"),
    OTHER("기타");

    private final String description;
}
