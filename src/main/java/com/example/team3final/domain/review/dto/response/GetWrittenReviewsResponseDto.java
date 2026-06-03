package com.example.team3final.domain.review.dto.response;

import java.util.List;

/**
 * 내가 작성한 후기 목록 조회 응답 DTO입니다.
 *
 * 사용자는 받은 후기 목록을 볼 수 없고, 본인이 작성한 후기만 확인할 수 있습니다.
 */
public record GetWrittenReviewsResponseDto(
        Long userId,
        String nickname,
        List<ReviewItemResponseDto> content
) {
}
