package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByAuthorIdInAndStatus(
            List<Long> authorIds,
            PostStatus status,
            Pageable pageable
    );

    // 특정 작성자(authorId)의 게시글만 페이징 조회
    Page<Post> findByAuthorId(Long authorId, Pageable pageable);
}
