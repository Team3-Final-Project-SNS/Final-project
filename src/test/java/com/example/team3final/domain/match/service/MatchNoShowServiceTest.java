package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.service.PostInternalService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchNoShowService 단위 테스트")
class MatchNoShowServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private MatchNoShowServiceImpl matchNoShowService;

    @Test
    @DisplayName("매칭 상태가 MATCHED이면 이의제기 상태로 변경한다")
    void markDisputed_shouldChangeMatchedToDisputed() {
        Match match = Match.builder()
                .postId(20L)
                .applicantId(2L)
                .applicantDeposit(200)
                .build();
        ReflectionTestUtils.setField(match, "id", 10L);
        when(matchInternalService.getMatchById(10L)).thenReturn(match);

        matchNoShowService.markDisputed(10L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.DISPUTED);
    }
}
