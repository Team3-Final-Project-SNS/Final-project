package com.example.team3final.domain.pointTransaction.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointSource {

    FREE("무료 포인트"),
    PAID("유료 포인트");

    private final String description;
}
