package com.example.team3final.domain.review.service;

import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.review.repository.ReviewBadTagRepository;
import com.example.team3final.domain.review.repository.ReviewGoodTagRepository;
import com.example.team3final.domain.review.repository.ReviewRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserMannerService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("리뷰 서비스 단위 테스트")
class ReviewServiceTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewGoodTagRepository reviewGoodTagRepository;

    @Mock
    private ReviewBadTagRepository reviewBadTagRepository;

    @Mock
    private ReviewAvoidanceService reviewAvoidanceService;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserMannerService userMannerService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private UserPointService userPointService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    @DisplayName("작성한 리뷰 목록을 조회하면 태그와 사용자 정보를 조합해 반환한다")
    void getWrittenReviews_shouldReturnWrittenReviews() {
        when(reviewRepository.findAllByWriterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(userInternalService.getUserInfo(1L))
                .thenReturn(new UserInfoDto(1L, "닉네임", "컴퓨터공학", "20", BigDecimal.valueOf(36.5), 1L));

        var response = reviewService.getWrittenReviews(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("다시 만나고 싶지 않은 사용자 목록 조회는 리뷰 회피 서비스에 위임한다")
    void getAvoidedUserIds_shouldDelegateToReviewAvoidanceService() {
        when(reviewAvoidanceService.getAvoidedUserIds(1L)).thenReturn(List.of(2L, 3L));

        List<Long> response = reviewService.getAvoidedUserIds(1L);

        assertThat(response).containsExactly(2L, 3L);
        verify(reviewAvoidanceService).getAvoidedUserIds(1L);
    }
}
