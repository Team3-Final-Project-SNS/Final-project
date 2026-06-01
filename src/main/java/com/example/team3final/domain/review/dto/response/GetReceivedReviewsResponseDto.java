package com.example.team3final.domain.review.dto.response;

import java.math.BigDecimal;
import java.util.List;


/**
 * 받은 후기 목록 조회 응답 DTO입니다.
 *
 *
 * 흐름:
 * {
 *   요청: "특정 유저가 받은 후기 목록 조회"
 *   조회: 사용자 정보 + 현재 매너 온도 + 받은 후기 Page
 *   응답: {
 *     userId,
 *     nickname,
 *     mannerTemperature,
 *     content: [ReviewItemResponseDto...],
 *     pageInfo
 *   }
 * }
 *
 * 조회 대상 유저의 기본 정보와 현재 매너 온도,
 * 받은 후기 목록, 페이지 정보를 함께 반환합니다.
 */
public record GetReceivedReviewsResponseDto(
        Long userId,
        String nickname,
        BigDecimal mannerTemperature,
        List<ReviewItemResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
