package com.example.team3final.domain.chat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatRoomType {

    ONE_TO_ONE("1:1 채팅방"),
    GROUP("그룹 채팅방");

    private final String description;

}