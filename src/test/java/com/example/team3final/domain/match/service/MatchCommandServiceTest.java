package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.RedisPostService;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchCommandService 단위 테스트")
class MatchCommandServiceTest {

    @Mock
    private MatchCreateService matchCreateService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private UserPointService userPointService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserLocationCleanupService userLocationCleanupService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPostService redisPostService;

    @InjectMocks
    private MatchCommandServiceImpl matchCommandService;

    @Test
    @DisplayName("매칭 생성은 매칭 생성 전용 서비스에 위임한다")
    void createMatch_shouldDelegateToMatchCreateService() {
        CreateMatchResponseDto expected = new CreateMatchResponseDto(
                10L, 20L, 1L, "작성자", 2L, "신청자", 300, 200, MatchStatus.MATCHED, 30L, LocalDateTime.now());
        when(matchCreateService.createMatch(20L, 2L)).thenReturn(expected);

        CreateMatchResponseDto response = matchCommandService.createMatch(20L, 2L);

        assertThat(response).isSameAs(expected);
        verify(matchCreateService).createMatch(20L, 2L);
    }

    @Test
    @DisplayName("존재하지 않는 매칭을 취소하면 실패한다")
    void cancelMatch_shouldThrowWhenMatchNotFound() {
        when(matchRepository.findByIdWithLock(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchCommandService.cancelMatch(10L, 1L, null))
                .isInstanceOf(MatchException.class);
    }
}
