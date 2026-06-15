package com.example.team3final.domain.post.scheduler;

import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 만료 스케줄러
 * 역할: meetAt(만남 시각)이 지났는데 아직 OPEN인 게시글을 EXPIRED로 전환
 * 주기: 매시 정각 (0 0 * * * *)
 * Component: Spring 빈으로 등록 — @Scheduled가 동작하려면 반드시 빈이어야 함
 * Slf4j: 로그 출력용 (Lombok이 자동으로 log 필드 생성)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostExpiredScheduler {

    private final PostRepository postRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserPointService userPointService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 매시 정각에 OPEN 상태인 만료 게시글을 EXPIRED로 일괄 전환
     * cron 표현식: "0 0 * * * *"
     *   0      → 0초에 실행
     *   0      → 0분에 실행
     *   *      → 모든 시(매 시간)
     *   *      → 모든 일
     *   *      → 모든 월
     *   *      → 모든 요일
     * Transactional: bulkExpireOpenPosts는 UPDATE 쿼리이므로 트랜잭션 필수
     *   - 스케줄러 자체에 @Transactional 붙이면 쿼리 실행~커밋까지 하나의 트랜잭션으로 묶임
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOpenPosts() {

        // 현재 시각 기준으로 만료 처리
        LocalDateTime now = LocalDateTime.now();

        log.info("[PostExpiredScheduler] 만료 처리 시작 - 기준 시각: {}", now);

        // 벌크 UPDATE 전에 만료 대상 게시글 목록 먼저 조회
        List<Post> expiredTargets = postRepository
                .findByStatusAndMeetAtBeforeAndDeletedAtIsNull(PostStatus.OPEN, now);

        int expiredCount = postRepository.bulkExpireOpenPosts(
                PostStatus.OPEN,    // WHERE status = 'OPEN'
                PostStatus.EXPIRED, // SET status = 'EXPIRED'
                now                 // AND meet_at < now
        );

        // 만료된 게시글 각각에 대해 알림 발송 + 포인트 환불 처리
        for (Post post : expiredTargets) {
            applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(post.getId()));
            // 40. 게시글 만료 알림 - 게시글 작성자에게
            notificationPublisher.sendPostExpired(post.getAuthorId(), post.getId());

            // 작성자 authorDeposit 전액 환불 (신규 추가)
            if (post.getAuthorDeposit() > 0) {
                userPointService.refundPoint(
                        post.getAuthorId(),     // 환불 대상: 게시글 작성자
                        post.getAuthorDeposit(),// 환불 금액: 예치했던 책임비 전액
                        post.getId()            // 참조 ID: Match 없으므로 postId로 대체
                );
            }
        }

        log.info("[PostExpiredScheduler] 만료 처리 완료 - {}건 EXPIRED 전환, 포인트 환불 처리", expiredCount);
    }
}
