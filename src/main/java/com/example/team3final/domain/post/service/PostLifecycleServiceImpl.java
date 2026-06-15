package com.example.team3final.domain.post.service;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Post 도메인의 상태 전환 및 생명주기 처리를 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostLifecycleServiceImpl implements PostLifecycleService {

    private final PostInternalService postInternalService;
    private final UserInternalService userInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void completePost(Long postId) {
        // 1. Post 조회
        Post post = postInternalService.getPostById(postId);

        // 이미 COMPLETED면 중복 처리 방지 (그룹 매칭에서 completePost 중복 호출 방어)
        if (post.getStatus() == PostStatus.COMPLETED) {
            return;
        }

        // 2. 도메인 메서드 호출 — 상태 전이 규칙은 엔티티가 책임
        post.complete();

        // 완료된 게시글은 더 이상 신청 대상이 아니므로 AI 추천 인덱스에서 제거합니다.
        publishPostVectorDeleteEvent(postId);

        log.info("[Post] 게시글 완료 처리 - postId: {}, status: {}",
                postId, post.getStatus());

    }

    /**
     * 동시성 테스트 전용: 게시글 상태 변경
     * MatchConcurrencyService에서 매칭 확정 시 OPEN → MATCHED 변경에 사용
     * 기존 completePost()는 COMPLETED 전용이라 별도로 분리
     * JPA 변경감지(Dirty Checking) 동작 원리:
     *   Transactional 안에서 조회한 엔티티를 수정하면
     *   트랜잭션 커밋 시점에 JPA가 변경사항을 감지하고 UPDATE 쿼리 자동 실행
     *   → postRepository.save(post) 를 명시적으로 호출하지 않아도 됨
     */
    @Override
    public void changePostStatus(Long postId, PostStatus status) {
        // 1. 기존 getPostById() 재사용 → 중복 조회 로직 없음
        Post post = postInternalService.getPostById(postId);

        // 2. 도메인 메서드로 상태 변경 (컨벤션: 상태 변경은 엔티티 내부 메서드가 책임)
        //    Post 엔티티에 changeStatus() 도메인 메서드가 있어야 함
        //    없다면 아래처럼 추가: public void changeStatus(PostStatus status) { this.status = status; }
        post.changeStatus(status);

        if (post.isOpen() && !post.isDeleted()) {
            publishPostVectorUpsertEvent(post);
        } else {
            publishPostVectorDeleteEvent(postId);
        }

        log.info("[Post] 게시글 상태 변경 - postId: {}, status: {}",
                postId, status);

        // 3. 명시적 save() 없음 → @Transactional + Dirty Checking이 자동 UPDATE 처리
    }

    private void publishPostVectorUpsertEvent(Post post) {
        if (applicationEventPublisher == null || post == null || !post.isOpen() || post.isDeleted()) {
            return;
        }

        // 이벤트에는 추천 검색에 필요한 스냅샷만 담습니다.
        // Listener가 AFTER_COMMIT에서 embedding 생성과 PostgreSQL upsert를 비동기로 수행합니다.
        applicationEventPublisher.publishEvent(
                new PostVectorUpsertEvent(
                        post.getId(),
                        post.getAuthorId(),
                        userInternalService.findUserById(post.getAuthorId()).getUniversityId(),
                        post.getStatus(),
                        post.getMeetAt(),
                        post.getPlaceName(),
                        post.getContent(),
                        post.getAuthorDeposit(),
                        post.getMaxApplicants(),
                        post.getCurrentApplicants(),
                        post.getPlaceLat(),
                        post.getPlaceLng()
                )
        );
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        if (applicationEventPublisher == null || postId == null) {
            return;
        }

        // 상태가 OPEN이 아니게 된 게시글은 postId만 알면 벡터 인덱스에서 제거할 수 있습니다.
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
