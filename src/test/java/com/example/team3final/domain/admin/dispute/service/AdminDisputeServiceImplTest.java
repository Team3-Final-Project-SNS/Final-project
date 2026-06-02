package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.common.exception.AdminException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDisputeServiceImplTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminDisputeServiceImpl adminDisputeService;

    @Mock
    private com.example.team3final.domain.dispute.service.DisputeService disputeService;
    @Mock
    private com.example.team3final.domain.match.service.MatchService matchService;
    @Mock
    private com.example.team3final.domain.post.service.PostService postService;
    @Mock
    private com.example.team3final.domain.user.service.UserPointService userPointService;
    @Mock
    private com.example.team3final.domain.notification.service.NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("이의제기 판정 - 성공")
    void judgeDispute_Success() {
        // given
        Long adminId = 1L;
        Long disputeId = 100L;
        Admin admin = mock(Admin.class);
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));
        
        Dispute dispute = mock(Dispute.class);
        given(dispute.getStatus()).willReturn(com.example.team3final.domain.dispute.enums.DisputeStatus.UNDER_REVIEW);
        given(dispute.getMatchId()).willReturn(200L);
        given(dispute.getSubmitterId()).willReturn(300L);
        given(disputeService.getDisputeById(disputeId)).willReturn(dispute);

        com.example.team3final.domain.match.entity.Match match = mock(com.example.team3final.domain.match.entity.Match.class);
        given(match.getPostId()).willReturn(400L);
        given(matchService.getMatchById(200L)).willReturn(match);

        com.example.team3final.domain.post.dto.response.PostMatchInfoDto postMatchInfo = 
            new com.example.team3final.domain.post.dto.response.PostMatchInfoDto(400L, 300L, java.time.LocalDateTime.now(), "place", 500);
        given(postService.getPostMatchInfo(400L)).willReturn(postMatchInfo);

        AdminJudgeDisputeRequestDto request = new AdminJudgeDisputeRequestDto();
        ReflectionTestUtils.setField(request, "status", com.example.team3final.domain.dispute.enums.DisputeStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "comment", "Approved");

        // when
        AdminJudgeDisputeResponseDto result = adminDisputeService.judgeDispute(adminId, disputeId, request);

        // then
        assertNotNull(result);
    }
}
