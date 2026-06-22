package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.MeetVerificationInternalServiceImpl;
import com.example.team3final.domain.meet.service.MeetVerificationNoShowServiceImpl;
import com.example.team3final.domain.meet.service.MeetVerificationQueryServiceImpl;
import com.example.team3final.domain.meet.service.MeetVerificationNoShowSettlementService;
import com.example.team3final.domain.meet.service.support.MeetExtensionSupport;
import com.example.team3final.domain.meet.service.support.MeetQrSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.location.service.UserLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("만남 인증 서비스 단위 테스트")
class PostStatusConcurrencyTest {

    @InjectMocks
    private MeetVerificationInternalServiceImpl internalService;

    @InjectMocks
    private MeetVerificationNoShowServiceImpl noShowService;

    @InjectMocks
    private MeetVerificationQueryServiceImpl queryService;

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private MeetExtensionSupport meetExtensionSupport;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private UserLocationCleanupService userLocationCleanupService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserLocationService userLocationService;

    @Mock
    private MeetVerificationContextReader contextReader;

    @Mock
    private MeetVerificationNoShowSettlementService noShowSettlementService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private MeetQrSupport meetQrSupport;

    @Test
    @DisplayName("만남 인증 초기화는 PENDING 상태의 인증 엔티티를 저장한다")
    void createPendingVerification_shouldSavePendingVerification() {
        internalService.createPendingVerification(100L);

        verify(meetVerificationRepository).save(any(MeetVerification.class));
    }

    @Test
    @DisplayName("매칭 ID로 만남 인증을 조회하면 저장소의 엔티티를 반환한다")
    void getByMatchId_shouldReturnMeetVerification() {
        MeetVerification meetVerification = MeetVerification.createPending(100L);
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.of(meetVerification));

        MeetVerification result = internalService.getByMatchId(100L);

        assertThat(result).isSameAs(meetVerification);
    }

    @Test
    @DisplayName("매칭 ID로 만남 인증을 조회할 때 엔티티가 없으면 예외가 발생한다")
    void getByMatchId_whenNotFound_shouldThrowException() {
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.empty());

        assertThrows(MeetException.class, () -> internalService.getByMatchId(100L));
    }

    @Test
    @DisplayName("GPS 노쇼 판정 대상이 없으면 추가 처리를 하지 않는다")
    void judgeGpsNoShow_whenNoTargets_shouldOnlyQueryPendingStatus() {
        given(meetVerificationRepository.findAllByStatus(VerificationStatus.PENDING)).willReturn(List.of());

        noShowService.judgeGpsNoShow();

        verify(meetVerificationRepository).findAllByStatus(VerificationStatus.PENDING);
    }

    @Test
    @DisplayName("QR 노쇼 판정 대상이 없으면 추가 처리를 하지 않는다")
    void judgeQrNoShow_whenNoTargets_shouldOnlyQueryVerifiedStatus() {
        given(meetVerificationRepository.findAllByStatusAndQrExpiresAtBefore(
                eq(VerificationStatus.VERIFIED), any(LocalDateTime.class)))
                .willReturn(List.of());

        noShowService.judgeQrNoShow();

        verify(meetVerificationRepository).findAllByStatusAndQrExpiresAtBefore(
                eq(VerificationStatus.VERIFIED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("노쇼 후보 조회는 노쇼 관련 상태 목록으로 저장소를 조회한다")
    void getNoShowCandidates_shouldQueryNoShowStatuses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<MeetVerification> page = new PageImpl<>(List.of(MeetVerification.createPending(100L)));
        given(meetVerificationRepository.findAllByStatusIn(anyList(), eq(pageable))).willReturn(page);

        Page<MeetVerification> result = noShowService.getNoShowCandidates(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 QR 조회는 작성자가 아니면 예외가 발생한다")
    void getMeetQrByPost_whenUserIsNotAuthor_shouldThrowException() {
        given(postInternalService.getPostInfo(200L))
                .willReturn(new PostInfoDto(
                        200L,
                        2L,
                        new BigDecimal("37.0"),
                        new BigDecimal("127.0"),
                        LocalDateTime.now().plusMinutes(10)
                ));

        assertThrows(MeetException.class, () -> queryService.getMeetQrByPost(1L, 200L));
    }

    @Test
    @DisplayName("연장 요청 만료 대상이 없으면 저장소 조회 후 종료한다")
    void expireTimeoutExtensions_whenNoTargets_shouldOnlyQueryRequestedExtensions() {
        given(meetVerificationRepository.findAllByExtensionStatusAndExtensionRequestedAtBefore(
                eq(ExtensionStatus.REQUESTED), any(LocalDateTime.class)))
                .willReturn(List.of());

        internalService.expireTimeoutExtensions();

        verify(meetVerificationRepository).findAllByExtensionStatusAndExtensionRequestedAtBefore(
                eq(ExtensionStatus.REQUESTED), any(LocalDateTime.class));
    }
}
