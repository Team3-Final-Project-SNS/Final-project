package com.example.team3final.domain.post.event;

import com.example.team3final.domain.ai.matching.repository.PostVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 게시글 상태 변화 이벤트를 받아 매칭 AI 전용 pgvector 인덱스를 동기화합니다.
 *
 * @TransactionalEventListener(AFTER_COMMIT)을 쓰는 이유는 MySQL 트랜잭션이 실제로 커밋된 뒤에만
 * 벡터 인덱스를 바꾸기 위해서입니다. 게시글 저장이 롤백되었는데 벡터 테이블만 갱신되는 불일치를 막습니다.
 *
 * @Async로 처리해 게시글 생성/수정/매칭 API 응답이 embedding 생성과 PostgreSQL upsert/delete를 기다리지 않게 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PostVectorRepository.class)
public class PostVectorEventListener {

    private final PostVectorRepository postVectorRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostVectorUpsert(PostVectorUpsertEvent event) {
        try {
            postVectorRepository.upsertPost(event);
            log.info("[PostVector] 게시글 벡터 upsert 완료 - postId: {}", event.postId());
        } catch (Exception e) {
            log.warn("[PostVector] 게시글 벡터 upsert 실패 - postId: {}", event.postId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostVectorDelete(PostVectorDeleteEvent event) {
        try {
            postVectorRepository.deletePost(event.postId());
            log.info("[PostVector] 게시글 벡터 삭제 완료 - postId: {}", event.postId());
        } catch (Exception e) {
            log.warn("[PostVector] 게시글 벡터 삭제 실패 - postId: {}", event.postId(), e);
        }
    }
}
