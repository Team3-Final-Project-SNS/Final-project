package com.example.team3final.domain.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportTargetType {

    POST("게시글"),
    USER("유저"),
    CHAT_MESSAGE("채팅 메세지");

    private final String description;
}
