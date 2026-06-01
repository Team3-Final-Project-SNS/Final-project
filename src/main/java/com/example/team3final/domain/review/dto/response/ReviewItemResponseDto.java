package com.example.team3final.domain.review.dto.response;

import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.enums.ReviewBadTag;
import com.example.team3final.domain.review.enums.ReviewGoodTag;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 받은 후기 목록의 단일 아이템 응답 DTO입니다.
 *
 *
 *  * 흐름:
 *  * {
 *  *   요청: "받은 후기 목록 조회"
 *  *   조회: reviews 1건 + 연결된 good/bad tags
 *  *   응답: "목록 안의 후기 1개"
 *  * }
 *
 *
 * 특정 후기를 작성한 사용자 정보와 선택된 좋아요/아쉬워요 태그,
 * 태그 기반 점수 변화량, 신고 필요 태그 포함 여부를 반환합니다.
 */
public record ReviewItemResponseDto(
        Long reviewId,
        Long matchId,
        Long writerId,
        String writerNickname,
        List<ReviewGoodTag> goodTags,
        List<ReviewBadTag> badTags,
        int tagScoreDelta,
        boolean reportNeeded,
        LocalDateTime createdAt
) {
    public static ReviewItemResponseDto of(
            Review review,
            String writerNickname,
            List<ReviewGoodTag> goodTags,
            List<ReviewBadTag> badTags,
            boolean reportNeeded
    ) {
        return new ReviewItemResponseDto(
                review.getId(),
                review.getMatchId(),
                review.getWriterId(),
                writerNickname,
                goodTags,
                badTags,
                review.getTagScoreDelta(),
                reportNeeded,
                review.getCreatedAt()
        );
    }
}
