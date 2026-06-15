package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.enums.PostStatus;

// Post 도메인의 상태 전환 및 생명주기 처리를 담당하는 서비스
public interface PostLifecycleService {

    /**
     * 게시글 상태를 COMPLETED로 전환 — 도메인 간 호출용
     * 사용처: 매칭 도메인(completeMatch) — QR 인증 완료 시 Post도 함께 종료
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    void completePost(Long postId);

    /**
     * 게시글 상태 변경 — 동시성 테스트에서 MatchConcurrencyService가 호출하는 전용 메서드
     * 내부 동작:
     *   post.changeStatus(status) 도메인 메서드 호출
     *   → JPA 변경감지(Dirty Checking)로 트랜잭션 종료 시 UPDATE 자동 발생
     *   → 명시적 save() 불필요
     * @param postId  변경할 게시글 ID
     * @param status  변경할 상태값
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    void changePostStatus(Long postId, PostStatus status);
}
