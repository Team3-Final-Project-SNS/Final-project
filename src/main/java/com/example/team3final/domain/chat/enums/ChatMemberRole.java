package com.example.team3final.domain.chat.enums;

import lombok.Getter;

@Getter
public enum ChatMemberRole {

    HOST("게시글 등록자"),
    GUEST("매칭 신청자");

    private final String description;

    ChatMemberRole(String description) {
        this.description = description;
    }
}