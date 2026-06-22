package com.example.team3final.domain.post.service;

import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostExpirationService 단위 테스트")
class PostExpirationServiceTest {

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private UserPointService userPointService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private RedisPostService redisPostService;

    @InjectMocks
    private PostExpirationServiceImpl postExpirationService;

    @Test
    @DisplayName("모집 인원이 부족한 열린 게시글이 만료되면 책임비를 환불하고 만료 상태로 변경한다")
    void process_shouldExpireOpenPostAndRefundDeposit() {
        Post post = post();
        when(postInternalService.getPostByIdWithLock(10L)).thenReturn(post);
        when(userPointService.hasAuthorDepositSettlement(1L, 10L)).thenReturn(false);

        postExpirationService.process(10L, LocalDateTime.now());

        assertThat(post.getStatus()).isEqualTo(PostStatus.EXPIRED);
        verify(userPointService).refundAuthorDeposit(eq(1L), eq(300), eq(10L), anyString());
        verify(notificationPublisher).sendPostExpired(1L, 10L);
    }

    private Post post() {
        Post post = Post.builder()
                .authorId(1L)
                .meetAt(LocalDateTime.now().minusMinutes(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(300)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", 10L);
        return post;
    }
}
