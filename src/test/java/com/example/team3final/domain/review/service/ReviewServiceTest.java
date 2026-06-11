package com.example.team3final.domain.review.service;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetWrittenReviewsResponseDto;
import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.entity.ReviewGoodTagEntity;
import com.example.team3final.domain.review.enums.ReviewBadTag;
import com.example.team3final.domain.review.enums.ReviewGoodTag;
import com.example.team3final.domain.review.repository.ReviewBadTagRepository;
import com.example.team3final.domain.review.repository.ReviewGoodTagRepository;
import com.example.team3final.domain.review.repository.ReviewRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewGoodTagRepository reviewGoodTagRepository;
    @Mock
    private ReviewBadTagRepository reviewBadTagRepository;
    @Mock
    private ReviewAvoidanceService reviewAvoidanceService;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;
    @Mock
    private UserService userService;
    @Mock
    private UserPointService userPointService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("후기 생성 - 성공")
    void createReview_Success() {
        // given
        Long matchId = 1L;
        Long writerId = 10L;
        Long authorId = 20L;
        CreateReviewRequestDto request = new CreateReviewRequestDto(List.of(ReviewGoodTag.KIND), List.of());

        Match match = mock(Match.class);
        given(match.getId()).willReturn(matchId);
        given(match.getPostId()).willReturn(100L);
        given(match.getStatus()).willReturn(MatchStatus.COMPLETED);
        given(match.getCompletedAt()).willReturn(LocalDateTime.now().minusDays(1));
        given(match.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(match.isApplicant(writerId)).willReturn(true);

        PostMatchInfoDto post = new PostMatchInfoDto(100L, authorId, LocalDateTime.now(), "place", 1000, 1, 2);

        given(matchService.getMatchById(matchId)).willReturn(match);
        given(postService.getPostMatchInfo(100L)).willReturn(post);
        given(reviewRepository.existsByMatchIdAndWriterId(matchId, writerId)).willReturn(false);
        given(matchService.getMatchIdsByPostId(anyLong())).willReturn(List.of(matchId));
        given(userService.getUserInfo(authorId)).willReturn(new UserInfoDto(authorId, "nickname", "major", "123456", new BigDecimal("36.5"), 1L));

        Review review = Review.builder().matchId(matchId).writerId(writerId).tagScoreDelta(1).build();
        ReflectionTestUtils.setField(review, "id", 1L);
        given(reviewRepository.save(any(Review.class))).willReturn(review);

        // when
        CreateReviewResponseDto result = reviewService.createReview(matchId, writerId, request);

        // then
        assertThat(result.targetNickname()).isEqualTo("nickname");
        verify(reviewRepository).save(any(Review.class));
        verify(userPointService).rewardReviewPoint(eq(writerId), anyInt(), eq(matchId));
    }

    @Test
    @DisplayName("작성한 리뷰 목록 조회 - 성공")
    void getWrittenReviews_Success() {
        Review review = Review.builder()
                .matchId(1L)
                .writerId(10L)
                .tagScoreDelta(1)
                .build();
        ReflectionTestUtils.setField(review, "id", 100L);
        given(reviewRepository.findAllByWriterIdOrderByCreatedAtDesc(10L)).willReturn(List.of(review));
        given(reviewGoodTagRepository.findByReviewIdIn(List.of(100L)))
                .willReturn(List.of(ReviewGoodTagEntity.builder()
                        .reviewId(100L)
                        .tag(ReviewGoodTag.KIND)
                        .build()));
        given(reviewBadTagRepository.findByReviewIdIn(List.of(100L))).willReturn(List.of());
        given(userService.getUserInfo(10L)).willReturn(new UserInfoDto(10L, "writer", "major", "123456", new BigDecimal("36.5"), 1L));

        GetWrittenReviewsResponseDto result = reviewService.getWrittenReviews(10L);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("회피 사용자 ID 목록 조회 - 성공")
    void getAvoidedUserIds_Success() {
        given(reviewAvoidanceService.getAvoidedUserIds(10L)).willReturn(List.of(20L));

        List<Long> result = reviewService.getAvoidedUserIds(10L);

        assertThat(result).containsExactly(20L);
    }

    @Test
    @DisplayName("회피 관계 존재 여부 조회 - 성공")
    void existsAvoidRelation_Success() {
        given(reviewAvoidanceService.existsAvoidRelation(10L, 20L)).willReturn(true);

        boolean result = reviewService.existsAvoidRelation(10L, 20L);

        assertThat(result).isTrue();
    }
}
