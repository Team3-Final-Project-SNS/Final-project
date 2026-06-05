package com.example.team3final.domain.review.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 후기에서 선택할 수 있는 아쉬운 점 태그입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ReviewBadTag {

    LATE("약속 시간에 늦었어요", -1),
    NO_REPLY("소통이 잘 안 되고 조금 어수선했어요", -1),
    UNCOMFORTABLE("생각했던 만남(모임)과 성격이 달랐어요", -1),
    BAD_MANNER("식사 매너가 아쉬웠어요", -1),
    DO_NOT_WANT_TO_MEET_AGAIN("다시 만나고 싶지 않아요", -1);

    private final String description;
    private final int scoreDelta;
}
