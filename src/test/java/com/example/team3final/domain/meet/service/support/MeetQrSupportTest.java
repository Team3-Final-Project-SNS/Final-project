package com.example.team3final.domain.meet.service.support;

import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchLifecycleService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("만남 QR 보조 컴포넌트 단위 테스트")
class MeetQrSupportTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private MatchLifecycleService matchLifecycleService;

    @InjectMocks
    private MeetQrSupport meetQrSupport;

    @Test
    @DisplayName("게시글에 매칭이 없으면 QR 토큰 소유자를 반환하지 않는다")
    void issuePostQrTokenIfNeeded_shouldReturnEmptyWhenNoMatches() {
        when(matchInternalService.getMatchIdsByPostId(20L)).thenReturn(List.of());

        Optional<MeetVerification> response = meetQrSupport.issuePostQrTokenIfNeeded(20L, LocalDateTime.now());

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("활성 매칭의 만남 인증에 공통 QR 토큰을 발급한다")
    void issuePostQrTokenIfNeeded_shouldIssueSharedToken() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        when(matchInternalService.getMatchIdsByPostId(20L)).thenReturn(List.of(10L));
        when(meetVerificationRepository.findAllByMatchIdIn(List.of(10L))).thenReturn(List.of(meetVerification));
        when(matchInternalService.getActiveMatchIdsByPostId(20L)).thenReturn(List.of(10L));

        Optional<MeetVerification> response = meetQrSupport.issuePostQrTokenIfNeeded(20L, LocalDateTime.now());

        assertThat(response).contains(meetVerification);
        assertThat(meetVerification.getQrToken()).isNotBlank();
        verify(matchLifecycleService).confirmPostMatchedForQrStage(20L);
    }
}
