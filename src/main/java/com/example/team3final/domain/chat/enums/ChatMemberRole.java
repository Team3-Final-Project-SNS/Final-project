package com.example.team3final.domain.chat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMemberRole {

    HOST("게시글 등록자"),
    GUEST("매칭 신청자");

    private final String description;

}