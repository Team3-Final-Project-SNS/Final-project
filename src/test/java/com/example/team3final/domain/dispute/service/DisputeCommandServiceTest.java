package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.MyDisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.meet.service.MeetVerificationQueryService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeCommandService 서비스 단위 테스트")
class DisputeCommandServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private MatchNoShowService matchNoShowService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private MeetVerificationQueryService meetVerificationQueryService;

    @Mock
    private MeetVerificationInternalService meetVerificationInternalService;

    @Mock
    private AdminService adminService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private DisputeCommandServiceImpl disputeCommandService;

    @Test
    @DisplayName("내 이의제기 상세 조회는 매치 존재 확인 후 제출자 기준 이의제기를 반환한다")
    void getDispute_shouldReturnDisputeDetail() {
        Dispute dispute = dispute(1L, 10L, 1L, DisputeStatus.SUBMITTED);
        when(disputeRepository.findByMatchIdAndSubmitterId(10L, 1L)).thenReturn(Optional.of(dispute));

        DisputeResponseDto result = disputeCommandService.getDispute(10L, 1L);

        assertThat(result.disputeId()).isEqualTo(1L);
        assertThat(result.matchId()).isEqualTo(10L);
        verify(matchInternalService).getMatchInfo(10L);
    }

    @Test
    @DisplayName("내 이의제기 상세 조회는 제출한 이의제기가 없으면 이의제기 예외를 던진다")
    void getDispute_shouldThrowWhenDisputeNotFound() {
        when(disputeRepository.findByMatchIdAndSubmitterId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeCommandService.getDispute(10L, 1L))
                .isInstanceOf(DisputeException.class);
    }

    @Test
    @DisplayName("내 이의제기 목록 조회는 제출자 ID 기준 최신순 목록을 반환한다")
    void getMyDisputes_shouldReturnMyDisputes() {
        Dispute dispute = dispute(1L, 10L, 1L, DisputeStatus.SUBMITTED);
        when(disputeRepository.findAllBySubmitterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(dispute));

        List<MyDisputeResponseDto> result = disputeCommandService.getMyDisputes(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).disputeId()).isEqualTo(1L);
        verify(disputeRepository).findAllBySubmitterIdOrderByCreatedAtDesc(1L);
    }

    private Dispute dispute(Long disputeId, Long matchId, Long submitterId, DisputeStatus status) {
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(submitterId)
                .disputeType(DisputeType.GPS_ERROR)
                .reason("GPS 오류")
                .build();
        ReflectionTestUtils.setField(dispute, "id", disputeId);
        ReflectionTestUtils.setField(dispute, "status", status);
        return dispute;
    }
}
