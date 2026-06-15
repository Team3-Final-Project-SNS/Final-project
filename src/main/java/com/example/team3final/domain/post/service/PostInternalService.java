package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

// Post 도메인의 타 도메인 호출용 내부 조회 기능을 제공하는 서비스
public interface PostInternalService {

    /**
     * 게시글 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     * 사용처: 매칭(createMatch 검증), GPS 인증(위경도 확인)
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostById(Long postId);

    // 삭제 포함 단건 조회
    Post getPostByIdIncludingDeleted(Long postId);

    /**
     * 게시글 정보 조회 — 도메인 간 호출용 (DTO 반환)
     * getPostById와 차이: 엔티티가 아닌 DTO 반환 → 단순 값 조회용
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    PostInfoDto getPostInfo(Long postId);

    /**
     * 게시글 매칭 정보 조회 — 도메인 간 호출용 (매칭 목록 조회 전용)
     */
    PostMatchInfoDto getPostMatchInfo(Long postId);

    /**
     * 게시글 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 매칭 정보에 묶인
     *         Post(meetAt, placeLat/Lng 등)를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     * Contract:
     *  - postIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 postId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     */
    Map<Long, PostInfoDto> getPostInfos(List<Long> postIds);

    /**
     * 게시글 매칭정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: 매칭 목록(getMatches) N+1 방지
     */
    Map<Long, PostMatchInfoDto> getPostMatchInfos(List<Long> postIds);

    // 비관적 락을 걸어서 게시글을 조회하는 메서드.
    // 관리자 삭제, 신고 접수처럼 동시성 제어가 필요한 흐름에서 사용.
    Post getPostByIdWithLock(Long postId);

    /**
     * 게시글 단건 조회 + 비관적 락 (NOWAIT) — 동시성 테스트 전략 A 전용
     * SELECT ... FOR UPDATE NOWAIT
     * → 다른 트랜잭션이 이 행을 이미 잠갔으면 기다리지 않고 즉시 LockTimeoutException 발생
     * → MatchConcurrencyService 전략 A에서 호출
     * 기존 getPostById()와의 차이:
     *   getPostById()    → 일반 SELECT, 락 없음
     *   이 메서드         → SELECT FOR UPDATE NOWAIT, 즉시 실패
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostWithPessimisticLockNowait(Long postId);

    /**
     * 게시글 단건 조회 + 비관적 락 (대기 O) — 동시성 테스트 전략 B 전용
     * SELECT ... FOR UPDATE (NOWAIT 없음)
     * → 다른 트랜잭션이 잠갔으면 innodb_lock_wait_timeout 설정값만큼 대기
     * → 대기 중 락이 해제되면 획득 성공, 시간 초과 시 예외
     * → MatchConcurrencyService 전략 B에서 호출
     * 테스트 시 주의: innodb_lock_wait_timeout 기본 50초 → 테스트 DB는 3초로 낮출 것
     *   SET innodb_lock_wait_timeout = 3;
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostWithPessimisticLock(Long postId);

    // ai 매칭 도메인에서 활용.
    List<Post> findAiMatchingCandidatePosts(
            List<Long> authorIds,
            Sort sort
    );

    // pgvector가 반환한 postId 후보를 MySQL posts 테이블에서 같은 학교, 작성자, OPEN 상태, 미래 약속 시간 기준으로 최종 검증합니다.
    List<Post> findAiMatchingCandidatePostsByIds(
            List<Long> postIds,
            List<Long> authorIds
    );
}
