package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchCreateService 단위 테스트")
class MatchCreateServiceTest {

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private MatchTransactionService matchTransactionService;

    @Test
    @DisplayName("본인 게시글에 매칭을 신청하면 락 획득 전 검증에서 실패한다")
    void createMatch_shouldThrowWhenSelfApply() {
        Post post = post();
        when(redissonClient.getLock("match:lock:post:20")).thenReturn(mock(RLock.class));
        when(postInternalService.getPostById(20L)).thenReturn(post);
        MatchCreateService matchCreateService = new MatchCreateService(
                postInternalService,
                redissonClient,
                notificationPublisher,
                matchTransactionService
        );

        assertThatThrownBy(() -> matchCreateService.createMatch(20L, 1L))
                .isInstanceOf(MatchException.class);
    }

    private Post post() {
        return Post.builder()
                .authorId(1L)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(300)
                .maxApplicants(2)
                .build();
    }
}
