package com.example.team3final.domain.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportTargetType {

    POST("게시글"),
    REVIEW("후기");

    private final String description;
}
