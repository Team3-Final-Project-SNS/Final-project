package com.example.team3final.domain.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportStatus {

    PENDING("처리 대기"),
    ACCEPTED("채택"),
    REJECTED("기각"),
    WITHDRAWN("취소");

    private final String description;
}
