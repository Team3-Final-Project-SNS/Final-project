package com.example.team3final.domain.location.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LocationRole {

    AUTHOR("등록자"),
    APPLICANT("신청자");

    private final String description;
}
