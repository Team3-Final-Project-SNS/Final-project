package com.example.team3final.domain.review.dto.request;

import com.example.team3final.domain.review.enums.ReviewBadTag;
import com.example.team3final.domain.review.enums.ReviewGoodTag;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequestDto(
        @Size(max = 5, message = "좋아요 태그는 최대 5개까지 선택할 수 있습니다.")
        List<ReviewGoodTag> goodTags,

        @Size(max = 5, message = "아쉬워요 태그는 최대 5개까지 선택할 수 있습니다.")
        List<ReviewBadTag> badTags
) {
}
