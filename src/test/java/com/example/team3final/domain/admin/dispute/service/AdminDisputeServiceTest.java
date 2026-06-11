package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminOverrideDisputeStatusRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDisputeServiceTest {

    @InjectMocks
    private AdminDisputeServiceImpl adminDisputeService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private DisputeService disputeService;
    @Mock
    private UserService userService;
    @Mock
    private MatchService matchService;
    @Mock
    private MeetVerificationService meetVerificationService;
    @Mock
    private ChatService chatService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("이의제기 목록 조회 - 성공")
    void getDisputes_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Dispute dispute = Dispute.builder().submitterId(10L).matchId(100L).build();
        Page<Dispute> page = new PageImpl<>(List.of(dispute), pageable, 1);
        given(disputeService.getDisputesForAdmin(any(), any())).willReturn(page);
        given(userService.getUserNicknameMap(any())).willReturn(Map.of(10L, "nickname"));

        // when
        PageResponseDto<GetAdminDisputesResponseDto> result = adminDisputeService.getDisputes(adminId, null, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).applicantNickname()).isEqualTo("nickname");
    }

    @Test
    @DisplayName("관리자 분쟁 판정 - 보류 성공")
    void judgeDispute_Hold_Success() {
        Dispute dispute = createDispute(1L, 100L, 10L);
        dispute.startReview(1L);
        Match match = mock(Match.class);
        AdminJudgeDisputeRequestDto request = new AdminJudgeDisputeRequestDto();
        ReflectionTestUtils.setField(request, "status", DisputeStatus.HOLD);
        ReflectionTestUtils.setField(request, "comment", "hold");
        given(adminRepository.findById(1L)).willReturn(Optional.of(mock(Admin.class)));
        given(disputeService.getDisputeById(1L)).willReturn(dispute);
        given(matchService.getMatchById(100L)).willReturn(match);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        AdminJudgeDisputeResponseDto result = adminDisputeService.judgeDispute(1L, 1L, request);

        assertThat(result.status()).isEqualTo(DisputeStatus.HOLD);
        verify(notificationPublisher).sendDisputePending(10L, 1L);
        verify(zSetOperations).add(anyString(), eq("1"), anyDouble());
    }

    @Test
    @DisplayName("관리자 분쟁 상태 강제 변경 - 성공")
    void overrideDisputeStatus_Success() {
        Dispute dispute = createDispute(1L, 100L, 10L);
        AdminOverrideDisputeStatusRequestDto request = new AdminOverrideDisputeStatusRequestDto();
        ReflectionTestUtils.setField(request, "status", DisputeStatus.REJECTED);
        ReflectionTestUtils.setField(request, "comment", "override");
        given(adminRepository.findById(1L)).willReturn(Optional.of(mock(Admin.class)));
        given(disputeService.getDisputeById(1L)).willReturn(dispute);

        AdminJudgeDisputeResponseDto result = adminDisputeService.overrideDisputeStatus(1L, 1L, request);

        assertThat(result.status()).isEqualTo(DisputeStatus.REJECTED);
        verify(notificationPublisher).sendDisputeResult(10L, 1L);
    }

    private Dispute createDispute(Long id, Long matchId, Long submitterId) {
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(submitterId)
                .disputeType(DisputeType.GPS_ERROR)
                .reason("reason")
                .build();
        ReflectionTestUtils.setField(dispute, "id", id);
        return dispute;
    }
}
