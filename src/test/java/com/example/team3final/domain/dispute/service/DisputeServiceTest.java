package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @InjectMocks
    private DisputeServiceImpl disputeService;

    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;
    @Mock
    private MeetVerificationService meetVerificationService;
    @Mock
    private AdminService adminService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("이의제기 생성 - 성공")
    void createDispute_Success() {
        // given
        Long matchId = 1L;
        Long userId = 10L;
        Long authorId = 20L;
        Long postId = 100L;
        
        CreateDisputeRequestDto request = new CreateDisputeRequestDto();
        ReflectionTestUtils.setField(request, "disputeType", DisputeType.GPS_ERROR);
        ReflectionTestUtils.setField(request, "reason", "REASON");

        MatchInfoDto matchInfo = new MatchInfoDto(matchId, postId, userId, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(postId, authorId, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10));

        MeetVerificationResponseDto meetInfo = mock(MeetVerificationResponseDto.class);
        given(meetInfo.verificationStatus()).willReturn(VerificationStatus.GUEST_NO_SHOW);
        given(meetInfo.noShowDecidedAt()).willReturn(LocalDateTime.now().minusHours(1));

        given(matchService.getMatchInfo(matchId)).willReturn(matchInfo);
        given(postService.getPostInfo(postId)).willReturn(postInfo);
        given(meetVerificationService.getMeetVerification(userId, matchId)).willReturn(meetInfo);
        given(disputeRepository.existsByMatchIdAndSubmitterId(matchId, userId)).willReturn(false);

        MeetVerification mv = mock(MeetVerification.class);
        given(meetVerificationService.getByMatchId(matchId)).willReturn(mv);
        given(adminService.getActiveAdminIds()).willReturn(List.of(100L));

        Dispute dispute = Dispute.builder().matchId(matchId).submitterId(userId).disputeType(DisputeType.GPS_ERROR).build();
        ReflectionTestUtils.setField(dispute, "id", 1L);
        given(disputeRepository.save(any(Dispute.class))).willReturn(dispute);

        // when
        CreateDisputeResponseDto result = disputeService.createDispute(matchId, userId, request);

        // then
        assertThat(result.matchId()).isEqualTo(matchId);
        verify(disputeRepository).save(any(Dispute.class));
        verify(notificationPublisher).sendDisputeSubmitted(eq(100L), any());
    }

    @Test
    @DisplayName("분쟁 조회 - 성공")
    void getDispute_Success() {
        Dispute dispute = createDisputeEntity(1L, 100L, 10L);
        given(matchService.getMatchInfo(100L)).willReturn(new MatchInfoDto(100L, 200L, 10L, MatchStatus.MATCHED));
        given(disputeRepository.findByMatchIdAndSubmitterId(100L, 10L)).willReturn(Optional.of(dispute));

        DisputeResponseDto result = disputeService.getDispute(100L, 10L);

        assertThat(result.disputeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("분쟁 엔티티 조회 - 성공")
    void getDisputeById_Success() {
        Dispute dispute = createDisputeEntity(1L, 100L, 10L);
        given(disputeRepository.findById(1L)).willReturn(Optional.of(dispute));

        Dispute result = disputeService.getDisputeById(1L);

        assertThat(result).isSameAs(dispute);
    }

    @Test
    @DisplayName("관리자 분쟁 목록 조회 - 성공")
    void getDisputesForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Dispute> page = new PageImpl<>(List.of(createDisputeEntity(1L, 100L, 10L)));
        given(disputeRepository.findAllByStatus(DisputeStatus.SUBMITTED, pageable)).willReturn(page);

        Page<Dispute> result = disputeService.getDisputesForAdmin(DisputeStatus.SUBMITTED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("분쟁 보유 매칭 ID 조회 - 성공")
    void getMatchIdsWithDispute_Success() {
        given(disputeRepository.findMatchIdsByMatchIdIn(List.of(1L, 2L))).willReturn(List.of(1L));

        Set<Long> result = disputeService.getMatchIdsWithDispute(List.of(1L, 2L));

        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("분쟁 재제출 - 성공")
    void reCreateDispute_Success() {
        CreateDisputeRequestDto request = new CreateDisputeRequestDto();
        ReflectionTestUtils.setField(request, "disputeType", DisputeType.GPS_ERROR);
        ReflectionTestUtils.setField(request, "reason", "again");
        Dispute parent = createDisputeEntity(1L, 100L, 10L);
        parent.startReview(1L);
        parent.hold(1L, "hold");
        Dispute saved = createDisputeEntity(2L, 100L, 10L);
        given(matchService.getMatchInfo(100L)).willReturn(new MatchInfoDto(100L, 200L, 10L, MatchStatus.MATCHED));
        given(postService.getPostInfo(200L)).willReturn(new PostInfoDto(200L, 20L, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusHours(1)));
        given(disputeRepository.findHoldDisputeByMatchIdAndSubmitterId(100L, 10L)).willReturn(Optional.of(parent));
        given(disputeRepository.existsByMatchIdAndSubmitterIdAndParentDisputeId(100L, 10L, 1L)).willReturn(false);
        given(disputeRepository.save(any(Dispute.class))).willReturn(saved);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(adminService.getActiveAdminIds()).willReturn(List.of(99L));

        CreateDisputeResponseDto result = disputeService.reCreateDispute(100L, 10L, request);

        assertThat(result.disputeId()).isEqualTo(2L);
        verify(zSetOperations).remove(anyString(), eq("1"));
        verify(notificationPublisher).sendDisputeSubmitted(99L, 2L);
    }

    private Dispute createDisputeEntity(Long id, Long matchId, Long submitterId) {
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(submitterId)
                .disputeType(DisputeType.GPS_ERROR)
                .reason("reason")
                .build();
        ReflectionTestUtils.setField(dispute, "id", id);
        ReflectionTestUtils.setField(dispute, "createdAt", LocalDateTime.now());
        return dispute;
    }
}
