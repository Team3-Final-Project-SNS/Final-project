package com.example.team3final.domain.post.service;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

// Post 도메인의 관리자 제재 및 복구 처리를 담당하는 서비스
public interface PostModerationService {

    // 게시글 강제 삭제 사유를 받아서 환불된 포인트 반환
    int forceDeletePost(Post post, String reason);

    // 강제 삭제 게시글 복구
    int restorePost(Post post);

    // 관리자 게시글 목록 조회
    Page<Post> getPostsForAdmin(
            List<Long> authorIds,
            PostStatus status,
            Boolean deleted,
            String keyword,
            Pageable pageable
    );

}
