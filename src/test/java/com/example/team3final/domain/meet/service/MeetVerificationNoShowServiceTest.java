package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.context.MeetVerificationBulkContext;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetVerificationNoShowService 단위 테스트")
class MeetVerificationNoShowServiceTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private UserLocationCleanupService userLocationCleanupService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private UserLocationService userLocationService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private MeetVerificationContextReader contextReader;

    @Mock
    private MeetVerificationNoShowSettlementService noShowSettlementService;

    @Mock
    private UserInternalService userInternalService;

    @InjectMocks
    private MeetVerificationNoShowServiceImpl meetVerificationNoShowService;

    @Test
    @DisplayName("관리자가 노쇼를 확정하면 확정 가능한 상태만 최종 확정 상태로 변경한다")
    void confirmNoShows_shouldConfirmNoShowStatuses() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        meetVerification.markApplicantNoShow();
        when(meetVerificationRepository.findAllByMatchIdIn(List.of(10L)))
                .thenReturn(List.of(meetVerification));

        meetVerificationNoShowService.confirmNoShows(List.of(10L));

        assertThat(meetVerification.getStatus()).isEqualTo(VerificationStatus.NO_SHOW_CONFIRMED);
    }

    @Test
    @DisplayName("노쇼 후보 목록 조회는 노쇼 상태 정책과 페이지 조건으로 저장소에 위임한다")
    void getNoShowCandidates_shouldDelegateToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<MeetVerification> page = Page.empty(pageable);
        when(meetVerificationRepository.findAllByStatusIn(
                MeetVerificationPolicy.NO_SHOW_STATUSES,
                pageable
        )).thenReturn(page);

        Page<MeetVerification> result = meetVerificationNoShowService.getNoShowCandidates(pageable);

        assertThat(result).isSameAs(page);
        verify(meetVerificationRepository).findAllByStatusIn(
                MeetVerificationPolicy.NO_SHOW_STATUSES,
                pageable
        );
    }

    @Test
    @DisplayName("노쇼 유예 시간이 지난 후보는 게시글 단위 정산 서비스로 위임한다")
    void judgeNoShowConfirmed_shouldDelegateSettlementByPost() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        meetVerification.markApplicantNoShow();
        MatchInfoDto matchInfo = new MatchInfoDto(10L, 20L, 2L, MatchStatus.MATCHED);
        MeetVerificationBulkContext bulkContext = new MeetVerificationBulkContext(
                Map.of(10L, matchInfo),
                Map.of()
        );
        when(meetVerificationRepository.findAllByStatusInAndNoShowDecidedAtBefore(
                eq(MeetVerificationPolicy.NO_SHOW_STATUSES),
                any(LocalDateTime.class)
        )).thenReturn(List.of(meetVerification));
        when(contextReader.loadBulkMatchContext(List.of(10L))).thenReturn(bulkContext);

        meetVerificationNoShowService.judgeNoShowConfirmed();

        verify(noShowSettlementService).settlePost(20L, List.of(10L));
    }
}
