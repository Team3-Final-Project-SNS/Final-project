package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.RedisPostService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchLifecycleService 단위 테스트")
class MatchLifecycleServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private UserLocationCleanupService userLocationCleanupService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private MeetVerificationInternalService meetVerificationInternalService;

    @InjectMocks
    private MatchLifecycleServiceImpl matchLifecycleService;

    @Test
    @DisplayName("시스템 매칭 취소는 매칭과 게시글을 취소하고 양측 포인트를 환불한다")
    void cancelMatchBySystem_shouldCancelMatchAndRefundBothSides() {
        Match match = match();
        Post post = post();
        when(matchInternalService.getMatchById(10L)).thenReturn(match);
        when(postInternalService.getPostById(20L)).thenReturn(post);

        matchLifecycleService.cancelMatchBySystem(10L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(post.getStatus()).isEqualTo(PostStatus.CANCELLED);
        verify(userPointService).refundAuthorDeposit(eq(1L), eq(300), eq(20L), anyString());
        verify(userPointService).refundApplicantDeposit(eq(2L), eq(200), eq(10L), anyString());
        verify(userLocationCleanupService).deleteLocationsByMatchId(10L);
        verify(chatInternalService).deactivateChatRoom(20L);
    }

    private Match match() {
        Match match = Match.builder()
                .postId(20L)
                .applicantId(2L)
                .applicantDeposit(200)
                .build();
        ReflectionTestUtils.setField(match, "id", 10L);
        return match;
    }

    private Post post() {
        Post post = Post.builder()
                .authorId(1L)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(300)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", 20L);
        post.match();
        return post;
    }
}
