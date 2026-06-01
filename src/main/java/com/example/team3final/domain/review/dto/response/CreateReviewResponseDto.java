package com.example.team3final.domain.review.dto.response;

import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.enums.ReviewBadTag;
import com.example.team3final.domain.review.enums.ReviewGoodTag;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 후기 작성 응답 DTO입니다.
 *
 *
 *  * 흐름:
 *  * {
 *  *   요청: "후기 작성"
 *  *   저장: reviews + review_good_tags/review_bad_tags
 *  *   응답: "방금 생성된 후기 결과"
 *  * }
 *
 *
 * 후기 생성 결과와 함께 실제 후기를 받은 사용자 정보,
 * 선택한 태그 목록, 태그 기반 점수 변화량,
 * 신고 필요 태그 포함 여부, 후기 작성 보상 포인트를 반환합니다.
 */
public record CreateReviewResponseDto(
        Long reviewId,
        Long matchId,
        Long targetId,
        String targetNickname,
        List<ReviewGoodTag> goodTags,
        List<ReviewBadTag> badTags,
        int tagScoreDelta,
        boolean reportNeeded,
        int rewardPoint,
        LocalDateTime createdAt
) {
    public static CreateReviewResponseDto of(
            Review review,
            Long targetId,
            String targetNickname,
            List<ReviewGoodTag> goodTags,
            List<ReviewBadTag> badTags,
            boolean reportNeeded
    ) {
        return new CreateReviewResponseDto(
                review.getId(),
                review.getMatchId(),
                targetId,
                targetNickname,
                goodTags,
                badTags,
                review.getTagScoreDelta(),
                reportNeeded,
                Review.REVIEW_REWARD_POINT,
                review.getCreatedAt()
        );
    }
}
