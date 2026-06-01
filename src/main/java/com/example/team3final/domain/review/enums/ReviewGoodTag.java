package com.example.team3final.domain.review.enums;

import lombok.Getter;

/**
 * 후기에서 선택할 수 있는 긍정 태그입니다.
 */
@Getter
public enum ReviewGoodTag {

    ON_TIME("시간 약속을 잘 지켜요", 1),
    KIND("친절해요", 1),
    GOOD_COMMUNICATION("대화가 잘 통해요", 1),
    CLEAN_MANNER("식사 매너가 좋아요", 1),
    WANT_MEET_AGAIN("다시 만나고 싶어요", 1);

    private final String description;
    private final int scoreDelta;

    ReviewGoodTag(String description, int scoreDelta) {
        this.description = description;
        this.scoreDelta = scoreDelta;
    }
}
