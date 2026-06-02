package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceImplTest {

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

    @InjectMocks
    private DisputeServiceImpl disputeService;

    @Test
    @DisplayName("이의제기 제출 - 성공")
    void createDispute_Success() {
        // given
        MatchInfoDto match = mock(MatchInfoDto.class);
        given(match.postId()).willReturn(10L);
        given(match.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(match);

        PostInfoDto post = mock(PostInfoDto.class);
        given(post.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(post);

        MeetVerificationResponseDto meet = mock(MeetVerificationResponseDto.class);
        given(meet.verificationStatus()).willReturn(VerificationStatus.HOST_NO_SHOW);
        given(meet.authorPlaceVerifiedAt()).willReturn(LocalDateTime.now());
        given(meet.noShowDecidedAt()).willReturn(LocalDateTime.now().minusHours(1));
        given(meetVerificationService.getMeetVerification(100L, 1L)).willReturn(meet);

        given(disputeRepository.existsByMatchIdAndSubmitterId(1L, 100L)).willReturn(false);

        Dispute dispute = mock(Dispute.class);
        given(dispute.getId()).willReturn(1L);
        given(disputeRepository.save(any(Dispute.class))).willReturn(dispute);
        given(adminService.getAdminId()).willReturn(999L);

        CreateDisputeRequestDto request = new CreateDisputeRequestDto();
        ReflectionTestUtils.setField(request, "disputeType", DisputeType.GPS_ERROR);
        ReflectionTestUtils.setField(request, "reason", "Reason");

        // when
        CreateDisputeResponseDto result = disputeService.createDispute(1L, 100L, request);

        // then
        assertNotNull(result);
        verify(disputeRepository).save(any(Dispute.class));
        verify(notificationPublisher).sendDisputeSubmitted(999L, 1L);
    }

    @Test
    @DisplayName("이의제기 제출 - 실패 (당사자 아님)")
    void createDispute_Fail_NotParticipant() {
        // given
        MatchInfoDto match = mock(MatchInfoDto.class);
        given(match.postId()).willReturn(10L);
        given(match.isParticipant(100L, 100L)).willReturn(false);
        given(matchService.getMatchInfo(1L)).willReturn(match);
        PostInfoDto post = mock(PostInfoDto.class);
        given(post.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(post);

        CreateDisputeRequestDto request = new CreateDisputeRequestDto();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.DisputeException.class, () -> {
            disputeService.createDispute(1L, 100L, request);
        });
    }

    @Test
    @DisplayName("이의제기 제출 - 실패 (노쇼 아님)")
    void createDispute_Fail_NotNoShow() {
        // given
        MatchInfoDto match = mock(MatchInfoDto.class);
        given(match.postId()).willReturn(10L);
        given(match.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(match);
        PostInfoDto post = mock(PostInfoDto.class);
        given(post.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(post);
        MeetVerificationResponseDto meet = mock(MeetVerificationResponseDto.class);
        given(meet.verificationStatus()).willReturn(VerificationStatus.PENDING);
        given(meetVerificationService.getMeetVerification(100L, 1L)).willReturn(meet);

        CreateDisputeRequestDto request = new CreateDisputeRequestDto();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.DisputeException.class, () -> {
            disputeService.createDispute(1L, 100L, request);
        });
    }

    @Test
    @DisplayName("이의제기 제출 - 실패 (장소 미인증)")
    void createDispute_Fail_NotVerified() {
        // given
        MatchInfoDto match = mock(MatchInfoDto.class);
        given(match.postId()).willReturn(10L);
        given(match.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(match);
        PostInfoDto post = mock(PostInfoDto.class);
        given(post.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(post);
        MeetVerificationResponseDto meet = mock(MeetVerificationResponseDto.class);
        given(meet.verificationStatus()).willReturn(VerificationStatus.HOST_NO_SHOW);
        given(meet.authorPlaceVerifiedAt()).willReturn(null);
        given(meetVerificationService.getMeetVerification(100L, 1L)).willReturn(meet);

        CreateDisputeRequestDto request = new CreateDisputeRequestDto();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.DisputeException.class, () -> {
            disputeService.createDispute(1L, 100L, request);
        });
    }

    @Test
    @DisplayName("이의제기 제출 - 실패 (이미 제출됨)")
    void createDispute_Fail_AlreadySubmitted() {
        // given
        MatchInfoDto match = mock(MatchInfoDto.class);
        given(match.postId()).willReturn(10L);
        given(match.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(match);
        PostInfoDto post = mock(PostInfoDto.class);
        given(post.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(post);
        MeetVerificationResponseDto meet = mock(MeetVerificationResponseDto.class);
        given(meet.verificationStatus()).willReturn(VerificationStatus.HOST_NO_SHOW);
        given(meet.authorPlaceVerifiedAt()).willReturn(LocalDateTime.now());
        given(meet.noShowDecidedAt()).willReturn(LocalDateTime.now().minusHours(1));
        given(meetVerificationService.getMeetVerification(100L, 1L)).willReturn(meet);
        given(disputeRepository.existsByMatchIdAndSubmitterId(1L, 100L)).willReturn(true);

        CreateDisputeRequestDto request = new CreateDisputeRequestDto();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.DisputeException.class, () -> {
            disputeService.createDispute(1L, 100L, request);
        });
    }
}
