package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GetPostResponseDto(
        Long postId,
        Long authorId,
        String authorNickname,
        String authorMajor,
        String authorStudentNumber,
        LocalDateTime meetAt,
        String placeName,
        BigDecimal placeLat,
        BigDecimal placeLng,
        String content,
        int authorDeposit,
        PostStatus status,
        boolean isMine,
        LocalDateTime createAt,
        LocalDateTime updateAt
) {
    /**
     * Entity + User 정보 + 조회자 컨텍스트 → DTO 변환
     *
     * @param post                Post 엔티티
     * @param authorNickname      작성자 닉네임 (User 도메인에서 조회)
     * @param authorMajor         작성자 학과 (User 도메인에서 조회)
     * @param authorStudentNumber 작성자 학번 (User 도메인에서 조회)
     * @param isMine              조회자가 작성자 본인인지 (Service에서 비교 후 전달)
     */
    public static GetPostResponseDto from(
            Post post,
            String authorNickname,
            String authorMajor,
            String authorStudentNumber,
            boolean isMine
    ) {
        return new GetPostResponseDto(
                post.getId(),
                post.getAuthorId(),
                authorNickname,
                authorMajor,
                authorStudentNumber,
                post.getMeetAt(),
                post.getPlaceName(),
                post.getPlaceLat(),
                post.getPlaceLng(),
                post.getContent(),
                post.getAuthorDeposit(),
                post.getStatus(),
                isMine,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
