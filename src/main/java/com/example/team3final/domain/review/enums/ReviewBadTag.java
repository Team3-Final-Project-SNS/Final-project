package com.example.team3final.domain.review.enums;

import lombok.Getter;

/**
 * 후기에서 선택할 수 있는 아쉬운 점 태그입니다.
 *
 * REPORT_NEEDED는 후기만으로 끝내기 어려운 상황을 표시하며,
 * 추후 신고 사유 입력 흐름과 연결할 수 있습니다.
 */
@Getter
public enum ReviewBadTag {

    // true일때만 신고 가능하게 하는 상태값.
    LATE("약속 시간에 늦었어요", -1, false),
    NO_REPLY("답장이 잘 오지 않았어요", -1, false),
    UNCOMFORTABLE("대화가 불편했어요", -1, false),
    BAD_MANNER("식사 매너가 아쉬웠어요", -1, false),
    REPORT_NEEDED("신고가 필요해요", -1, true);

    private final String description;
    private final int scoreDelta;
    private final boolean reportable;

    ReviewBadTag(String description, int scoreDelta, boolean reportable) {
        this.description = description;
        this.scoreDelta = scoreDelta;
        this.reportable = reportable;
    }
}
