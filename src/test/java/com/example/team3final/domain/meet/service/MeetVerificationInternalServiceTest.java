package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetExtensionSupport;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetVerificationInternalService 단위 테스트")
class MeetVerificationInternalServiceTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private MeetExtensionSupport meetExtensionSupport;

    @InjectMocks
    private MeetVerificationInternalServiceImpl meetVerificationInternalService;

    @Test
    @DisplayName("매칭 생성 후 대기 상태 만남 인증 정보를 저장한다")
    void createPendingVerification_shouldSavePendingVerification() {
        meetVerificationInternalService.createPendingVerification(10L);

        ArgumentCaptor<MeetVerification> captor = ArgumentCaptor.forClass(MeetVerification.class);
        verify(meetVerificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("매칭 ID로 만남 인증 정보를 조회한다")
    void getByMatchId_shouldReturnMeetVerification() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        when(meetVerificationRepository.findByMatchId(10L)).thenReturn(Optional.of(meetVerification));

        MeetVerification response = meetVerificationInternalService.getByMatchId(10L);

        assertThat(response).isSameAs(meetVerification);
    }

    @Test
    @DisplayName("만남 인증 정보가 없으면 조회에 실패한다")
    void getByMatchId_shouldThrowWhenNotFound() {
        when(meetVerificationRepository.findByMatchId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetVerificationInternalService.getByMatchId(10L))
                .isInstanceOf(MeetException.class);
    }
}
