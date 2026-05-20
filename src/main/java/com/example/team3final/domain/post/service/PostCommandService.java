package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;

public interface PostCommandService {

    /**
     * 게시글 작성
     *
     * @param authorId 작성자 ID (Controller에서 인증 정보로 추출해 전달)
     * @param request  게시글 작성 요청 DTO
     * @return 생성된 게시글 정보
     */
    CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request);

    /**
     * 게시글 상태를 COMPLETED로 전환 — 도메인 간 호출용
     *
     * 사용처:
     * - 매칭 도메인(completeMatch): QR 인증 완료로 매칭이 끝났을 때 Post도 함께 종료
     *
     * ※ 상태 전이 규칙(현재 상태가 어떻든 COMPLETED로 가는지 등)은 엔티티의 complete() 메서드가 책임짐
     *   Service는 단순히 조회 → 호출만 담당
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    void completePost(Long postId);
}
