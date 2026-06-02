package com.example.team3final.domain.ai.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한끼팟에서 제공하는 AI 기능을 구분하는 enum입니다.
 *
 * AI 호출 메트릭, 대화 세션, fallback 처리에서
 * 어떤 AI 기능의 요청인지 식별하기 위해 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiFeature {

    MATCHING,
    SUPPORT,
    REPORT,
    MODERATION
}
