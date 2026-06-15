package com.example.team3final.domain.post.scheduler;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 만료 스케줄러
 * 역할: meetAt이 지난 OPEN 게시글을 참여 인원 정책에 따라 MATCHED 또는 EXPIRED로 전환
 * 주기: 매분 정각 (0 * * * * *)
 * Component: Spring 빈으로 등록 — @Scheduled가 동작하려면 반드시 빈이어야 함
 * Slf4j: 로그 출력용 (Lombok이 자동으로 log 필드 생성)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostExpiredScheduler {

    private final PostRepository postRepository;
    private final PostExpirationProcessor postExpirationProcessor;

    /**
     * 매시 정각에 OPEN 상태인 만료 게시글을 EXPIRED로 일괄 전환
     * cron 표현식: "0 0 * * * *"
     *   0      → 0초에 실행
     *   0      → 0분에 실행
     *   *      → 모든 시(매 시간)
     *   *      → 모든 일
     *   *      → 모든 월
     *   *      → 모든 요일
     * 각 게시글은 PostExpirationProcessor의 REQUIRES_NEW 트랜잭션에서 독립 처리한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireOpenPosts() {

        // 현재 시각 기준으로 만료 처리
        LocalDateTime now = LocalDateTime.now();

        log.info("[PostExpiredScheduler] 만료 처리 시작 - 기준 시각: {}", now);

        List<Post> dueTargets = postRepository
                .findByStatusAndMeetAtBeforeAndDeletedAtIsNull(PostStatus.OPEN, now);

        for (Post post : dueTargets) {
            try {
                // 한 Post의 실패가 나머지 만료/매칭 전환을 막지 않도록 단건 트랜잭션으로 처리한다.
                postExpirationProcessor.process(post.getId(), now);
            } catch (Exception e) {
                log.error(
                        "[PostExpiredScheduler] 단건 처리 실패 - postId={}, exception={}, message={}",
                        post.getId(),
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        }

        log.info("[PostExpiredScheduler] 처리 완료 - 대상 {}건", dueTargets.size());
    }
}
