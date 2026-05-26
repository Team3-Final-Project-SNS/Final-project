package com.example.team3final.domain.user.enums;

import lombok.Getter;

@Getter
public enum Gender {
    MALE("남성"),    // 남성
    FEMALE("여성");   // 여성

    private final String description;

    Gender(String description) {this.description = description;}
}
