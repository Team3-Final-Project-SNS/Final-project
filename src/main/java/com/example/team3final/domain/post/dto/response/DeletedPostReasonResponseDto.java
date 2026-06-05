package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;

import java.time.LocalDateTime;

public record DeletedPostReasonResponseDto (

        Long postId,
        String placeName,       // 어떤 글이었는지 식별용
        String deleteReason,    // 관리자가 입력한 삭제 사유
        LocalDateTime deletedAt // 삭제 처리 시각
) {
    public static DeletedPostReasonResponseDto from(Post post) {
        return new DeletedPostReasonResponseDto(
                post.getId(),
                post.getPlaceName(),
                post.getDeleteReason(),
                post.getDeletedAt()
        );
    }
}
