package com.example.team3final.domain.post.service;

import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostExpirationServiceImpl implements PostExpirationService {

    private final PostInternalService postInternalService;
    private final NotificationPublisher notificationPublisher;
    private final UserPointService userPointService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long postId, LocalDateTime now) {
        Post post = postInternalService.getPostByIdWithLock(postId);

        // 조회 이후 신청/취소가 일어났을 수 있으므로 락 안에서 조건을 다시 확인한다.
        if (post.getStatus() != PostStatus.OPEN || !post.getMeetAt().isBefore(now)) {
            return;
        }

        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(post.getId()));

        if (post.getCurrentApplicants() >= 2) {
            // 작성자 외 신청자가 한 명이라도 있으면 정원 미달이어도 정책상 모임이 성립한다.
            post.match();
            return;
        }

        // 작성자 혼자 남은 게시글만 모집 실패로 만료하고 등록자 책임비를 돌려준다.
        post.expire();
        if (post.getAuthorDeposit() > 0
                && !userPointService.hasSettlement(post.getAuthorId(), post.getId())) {
            userPointService.refundPoint(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }

        // 만료 상태와 환불이 실제 커밋된 경우에만 사용자 알림을 발행한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationPublisher.sendPostExpired(post.getAuthorId(), post.getId());
            }
        });
    }
}
