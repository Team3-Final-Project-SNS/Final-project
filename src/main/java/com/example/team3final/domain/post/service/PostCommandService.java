package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.DeletePostResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;

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

    /**
     * 게시글 수정 — Controller에서 직접 호출 (명세서 4.4 updatePost)
     *
     * @param postId    수정할 게시글 ID
     * @param userId    요청자 ID (작성자 본인 검증용)
     * @param request   부분 수정 요청 DTO (모든 필드 nullable)
     * @return 수정된 게시글 정보
     * @throws PostException POST_001/201/202/102 — 각 단계별 검증 실패
     */
    UpdatePostResponseDto updatePost(Long postId, Long userId, UpdatePostRequestDto request);

    /**
     * 게시글 삭제 — Controller에서 직접 호출 (명세서 4.5 deletePost)
     *
     * @param postId 삭제할 게시글 ID
     * @param userId 요청자 ID (작성자 본인 검증용)
     * @return 삭제된 게시글 ID + 환불 포인트
     * @throws PostException POST_001/005/006 — 각 단계별 검증 실패
     */
    DeletePostResponseDto deletePost(Long postId, Long userId);
}
