package com.example.team3final.domain.ai.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiAdminCategory {

    DASHBOARD("대시보드"),
    POST("게시글"),
    REPORT("신고"),
    INQUIRY("고객 문의"),
    DISPUTE("이의제기"),
    USER("유저"),
    PAYMENT("주문 결제"),
    FAQ("FAQ"),
    GENERAL("일반 질문");

    private final String description;
}
