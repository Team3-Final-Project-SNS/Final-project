package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.RedisPostService;
import com.example.team3final.domain.report.service.ReportInternalService;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchTransactionService 단위 테스트")
class MatchTransactionServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private ReviewAvoidanceService reviewAvoidanceService;

    @Mock
    private ReportInternalService reportInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPostService redisPostService;

    @InjectMocks
    private MatchTransactionService matchTransactionService;

    @Test
    @DisplayName("트랜잭션 내부에서도 본인 게시글 매칭 신청은 차단한다")
    void createMatchInTransaction_shouldThrowWhenSelfApply() {
        when(postInternalService.getPostWithPessimisticLock(20L)).thenReturn(post());

        assertThatThrownBy(() -> matchTransactionService.createMatchInTransaction(20L, 1L))
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
