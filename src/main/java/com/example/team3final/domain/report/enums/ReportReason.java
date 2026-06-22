package com.example.team3final.domain.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportReason {

    SPAM("스팸"),
    OBSCENE("음란물"),
    FRAUD("사기"),
    ABUSE("욕설/비방"),
    OTHER("기타");

    private final String description;
}
