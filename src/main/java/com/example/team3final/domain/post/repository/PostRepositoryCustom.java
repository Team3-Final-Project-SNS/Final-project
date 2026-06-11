package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostRepositoryCustom {

    // 관리자 게시글 전체 조회
    // PostRepository의 findAllForAdmin 메서드명과 반환 타입을 유지
    // 서비스 계층 코드를 수정하지 않기 위해 외부 시그니처는 그대로 두고,
    // 실제 QueryDSL 조회 로직은 searchPostsForAdmin() 공통 메서드에 위임
    Page<Post> findAllForAdmin(PostStatus status, String keyword, Pageable pageable);

    // 관리자 게시글 조회 - 작성자 ID 목록 필터 포함
    // universityId 또는 authorNickname 조건은 서비스 계층에서 authorIds로 변환된 뒤 전달되는데,
    // 이 메서드도 외부 시그니처는 그대로 유지하고, 내부 공통 조회 메서드에 위임
    Page<Post> findAllForAdminByAuthorIds(
            List<Long> authorIds,
            PostStatus status,
            String keyword,
            Pageable pageable
    );
}
