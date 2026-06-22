package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.dispute.service.DisputeInternalService;
import com.example.team3final.domain.match.context.NoShowSettlementResult;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.meet.context.MeetVerificationBulkContext;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetVerificationNoShowSettlementService 단위 테스트")
class MeetVerificationNoShowSettlementServiceTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private MatchNoShowService matchNoShowService;

    @Mock
    private DisputeInternalService disputeInternalService;

    @Mock
    private MeetVerificationContextReader contextReader;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private MeetVerificationNoShowSettlementServiceImpl meetVerificationNoShowSettlementService;

    @Test
    @DisplayName("정산 대상 후보가 없으면 매치 정산을 호출하지 않는다")
    void settlePost_shouldReturnWhenCandidatesAreEmpty() {
        when(meetVerificationRepository.findAllByMatchIdInWithLock(List.of(10L)))
                .thenReturn(List.of());

        meetVerificationNoShowSettlementService.settlePost(20L, List.of(10L));

        verifyNoInteractions(matchNoShowService);
    }

    @Test
    @DisplayName("노쇼 정산이 완료되면 검증 상태를 확정하고 대상자에게 확정 알림을 보낸다")
    void settlePost_shouldConfirmVerificationAndSendNotifications() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        meetVerification.markApplicantNoShow();
        MatchInfoDto matchInfo = new MatchInfoDto(10L, 20L, 2L, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(
                20L,
                1L,
                BigDecimal.valueOf(37.1),
                BigDecimal.valueOf(127.1),
                LocalDateTime.now().plusDays(1)
        );
        MeetVerificationBulkContext bulkContext = new MeetVerificationBulkContext(
                Map.of(10L, matchInfo),
                Map.of(20L, postInfo)
        );
        when(meetVerificationRepository.findAllByMatchIdInWithLock(List.of(10L)))
                .thenReturn(List.of(meetVerification));
        when(matchInternalService.getMatchIdsByPostId(20L)).thenReturn(List.of(10L));
        when(matchInternalService.getMatchInfos(List.of(10L))).thenReturn(Map.of(10L, matchInfo));
        when(disputeInternalService.getMatchIdsWithActiveDispute(List.of(10L))).thenReturn(Set.of());
        when(matchNoShowService.finalizeNoShows(eq(20L), anyList()))
                .thenReturn(new NoShowSettlementResult(20L, List.of(10L)));
        when(contextReader.loadBulkMatchContext(List.of(10L))).thenReturn(bulkContext);

        meetVerificationNoShowSettlementService.settlePost(20L, List.of(10L));

        assertThat(meetVerification.getStatus()).isEqualTo(VerificationStatus.NO_SHOW_CONFIRMED);
        assertThat(meetVerification.isNoShowConfirmedSent()).isTrue();
        verify(notificationPublisher).sendNoShowConfirmed(1L, 10L);
        verify(notificationPublisher).sendNoShowConfirmed(2L, 10L);
    }
}
