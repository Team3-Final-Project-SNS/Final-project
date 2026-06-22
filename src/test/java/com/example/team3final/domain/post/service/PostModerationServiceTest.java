package com.example.team3final.domain.post.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostModerationService 단위 테스트")
class PostModerationServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserPointService userPointService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private RedisPostService redisPostService;

    @InjectMocks
    private PostModerationServiceImpl postModerationService;

    @Test
    @DisplayName("게시글을 강제 삭제하면 작성자 책임비를 환불하고 게시글을 삭제 처리한다")
    void forceDeletePost_shouldRefundAndDeletePost() {
        Post post = post();
        when(matchRepository.findAllByPostIdAndStatusOrderByIdAsc(10L, MatchStatus.MATCHED)).thenReturn(List.of());

        int refundedPoint = postModerationService.forceDeletePost(post, "운영 정책 위반");

        assertThat(refundedPoint).isEqualTo(300);
        assertThat(post.isDeleted()).isTrue();
        verify(userPointService).refundAuthorDeposit(eq(1L), eq(300), eq(10L), anyString());
        verify(notificationPublisher).sendPostDeleted(1L, 10L);
    }

    @Test
    @DisplayName("관리자 게시글 목록 조회에서 작성자 필터가 없으면 전체 관리자 조회를 사용한다")
    void getPostsForAdmin_shouldUseAllAdminQueryWhenAuthorIdsNull() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(postRepository.findAllForAdmin(PostStatus.OPEN, false, "밥", pageable)).thenReturn(Page.empty(pageable));

        postModerationService.getPostsForAdmin(null, PostStatus.OPEN, false, "밥", pageable);

        verify(postRepository).findAllForAdmin(PostStatus.OPEN, false, "밥", pageable);
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
        ReflectionTestUtils.setField(post, "id", 10L);
        return post;
    }
}
