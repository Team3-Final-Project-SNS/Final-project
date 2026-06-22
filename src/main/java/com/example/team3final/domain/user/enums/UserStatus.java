package com.example.team3final.domain.user.enums;

import lombok.Getter;

@Getter
public enum UserStatus {
    ACTIVE("활성화"),     // 활성화
    SUSPENDED("정지"),  // 정지
    WITHDRAWN("탈퇴");  // 탈퇴

    private final String description;

    UserStatus(String description) {this.description = description;}
}
