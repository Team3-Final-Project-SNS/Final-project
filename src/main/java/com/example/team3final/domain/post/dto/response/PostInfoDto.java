package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 게시글 정보 조회용 DTO — 도메인 간 호출 전용
 *
 * 사용처:
 * - GPS 인증: 약속 장소 좌표 + 만남 시간 검증
 *
 * ※ Controller 응답으로는 사용하지 않음 (그건 4.3 getPost용 GetPostResponseDto가 따로)
 * ※ 엔티티 메서드(post.match() 등) 호출이 필요하면 PostQueryService.getPostById(Long) 사용
 */
public record PostInfoDto(
        Long postId,
        Long authorId,
        BigDecimal placeLat,
        BigDecimal placeLng,
        LocalDateTime meetAt
) {
    /**
     * Entity → DTO 변환 정적 팩토리
     *
     * - new로 직접 생성하면 필드 순서 실수 위험 → from() 메서드로 일관된 변환
     * - 필요한 필드만 추려서 매핑 (다른 필드는 GPS 도메인이 알 필요 없음)
     */
    public static PostInfoDto from(Post post) {
        return new PostInfoDto(
                post.getId(),
                post.getAuthorId(),
                post.getPlaceLat(),
                post.getPlaceLng(),
                post.getMeetAt()
        );
    }
}
