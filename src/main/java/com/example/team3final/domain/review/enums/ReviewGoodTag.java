package com.example.team3final.domain.review.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 후기에서 선택할 수 있는 긍정 태그입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ReviewGoodTag {

    ON_TIME("시간 약속을 잘 지켜요", 1),
    KIND("대화 코드가 잘 맛았어요", 1),
    GOOD_COMMUNICATION("부감 없이 편하게 밥 먹는 분귀기였어요", 1),
    CLEAN_MANNER("식사 매너가 좋아요", 1),
    WANT_MEET_AGAIN("다음에 또 한 끼 같이하고 싶어요", 1);

    private final String description;
    private final int scoreDelta;
}
