package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.dispute.service.DisputeQueryService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.response.AcceptMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.CreateMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.NoShowMatchResponseDto;
import com.example.team3final.domain.meet.dto.response.RejectMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetVerificationServiceTest {

    @InjectMocks
    private MeetVerificationServiceImpl meetVerificationService;

    @Mock
    private MeetVerificationRepository meetVerificationRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private DisputeQueryService disputeQueryService;

    @Test
    @DisplayName("만남 연장 요청 - 성공")
    void createMeetExtension_Success() {
        // given
        Long userId = 1L;
        Long matchId = 100L;
        Long postId = 200L;
        Long authorId = 2L;

        MeetVerification mv = spy(MeetVerification.createPending(matchId));
        MatchInfoDto matchInfo = new MatchInfoDto(matchId, postId, userId, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(postId, authorId, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10));

        given(meetVerificationRepository.findByMatchId(matchId)).willReturn(Optional.of(mv));
        given(matchService.getMatchInfo(matchId)).willReturn(matchInfo);
        given(postService.getPostInfo(postId)).willReturn(postInfo);
        given(matchService.getMatchIdsByPostId(postId)).willReturn(List.of(matchId));
        given(matchService.getMatchInfos(anyList())).willReturn(Map.of(matchId, matchInfo));
        given(meetVerificationRepository.findAllByMatchIdInWithLock(anyList())).willReturn(List.of(mv));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(userService.getUserInfo(userId)).willReturn(new UserInfoDto(userId, "nickname", "major", "123456", new BigDecimal("36.5"), 1L));

        // when
        CreateMeetExtensionResponseDto result = meetVerificationService.createMeetExtension(userId, matchId);

        // then
        assertThat(result.extensionStatus()).isEqualTo(ExtensionStatus.REQUESTED);
        verify(notificationPublisher).sendMeetExtendRequested(eq(authorId), eq(matchId));
    }

    @Test
    @DisplayName("만남 연장 수락 - 성공")
    void acceptMeetExtension_Success() {
        // given
        Long authorId = 2L;
        Long matchId = 100L;
        Long postId = 200L;
        Long applicantId = 1L;

        MeetVerification mv = spy(MeetVerification.createPending(matchId));
        mv.requestExtension(applicantId);

        MatchInfoDto matchInfo = new MatchInfoDto(matchId, postId, applicantId, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(postId, authorId, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10));

        given(meetVerificationRepository.findByMatchId(matchId)).willReturn(Optional.of(mv));
        given(matchService.getMatchInfo(matchId)).willReturn(matchInfo);
        given(postService.getPostInfo(postId)).willReturn(postInfo);
        given(matchService.getMatchIdsByPostId(postId)).willReturn(List.of(matchId));
        given(matchService.getMatchInfos(anyList())).willReturn(Map.of(matchId, matchInfo));
        given(meetVerificationRepository.findAllByMatchIdInWithLock(anyList())).willReturn(List.of(mv));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // when
        AcceptMeetExtensionResponseDto result = meetVerificationService.acceptMeetExtension(authorId, matchId);

        // then
        assertThat(result.extensionStatus()).isEqualTo(ExtensionStatus.ACCEPTED);
        verify(notificationPublisher).sendMeetExtendAccepted(eq(applicantId), eq(matchId));
    }

    @Test
    @DisplayName("만남 연장 거절 - 성공")
    void rejectMeetExtension_Success() {
        // given
        Long authorId = 2L;
        Long matchId = 100L;
        Long postId = 200L;
        Long applicantId = 1L;

        MeetVerification mv = spy(MeetVerification.createPending(matchId));
        mv.requestExtension(applicantId);

        MatchInfoDto matchInfo = new MatchInfoDto(matchId, postId, applicantId, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(postId, authorId, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10));

        given(meetVerificationRepository.findByMatchId(matchId)).willReturn(Optional.of(mv));
        given(matchService.getMatchInfo(matchId)).willReturn(matchInfo);
        given(postService.getPostInfo(postId)).willReturn(postInfo);
        given(matchService.getMatchIdsByPostId(postId)).willReturn(List.of(matchId));
        given(matchService.getMatchInfos(anyList())).willReturn(Map.of(matchId, matchInfo));
        given(meetVerificationRepository.findAllByMatchIdInWithLock(anyList())).willReturn(List.of(mv));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // when
        RejectMeetExtensionResponseDto result = meetVerificationService.rejectMeetExtension(authorId, matchId);

        // then
        assertThat(result.extensionStatus()).isEqualTo(ExtensionStatus.REJECTED);
        verify(notificationPublisher).sendMeetExtendRejected(eq(applicantId), eq(matchId));
    }

    @Test
    @DisplayName("GPS 장소 인증 - 만남 인증 없음")
    void createPlaceVerification_NotFound() {
        PlaceVerificationRequestDto request = new PlaceVerificationRequestDto();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "currentLat", new BigDecimal("37.0"));
        org.springframework.test.util.ReflectionTestUtils.setField(request, "currentLng", new BigDecimal("127.0"));
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.empty());

        assertThrows(MeetException.class, () -> meetVerificationService.createPlaceVerification(1L, 100L, request));
    }

    @Test
    @DisplayName("게시글 QR 조회 - 작성자 아님")
    void getMeetQrByPost_NotAuthor() {
        given(postService.getPostInfo(200L))
                .willReturn(new PostInfoDto(200L, 2L, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10)));

        assertThrows(MeetException.class, () -> meetVerificationService.getMeetQrByPost(1L, 200L));
    }

    @Test
    @DisplayName("QR 스캔 - 만남 인증 없음")
    void createQrScan_NotFound() {
        QrScanRequestDto request = new QrScanRequestDto();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "qrToken", "token");
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.empty());

        assertThrows(MeetException.class, () -> meetVerificationService.createQrScan(1L, 100L, request));
    }

    @Test
    @DisplayName("만남 인증 조회 - 만남 인증 없음")
    void getMeetVerification_NotFound() {
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.empty());

        assertThrows(MeetException.class, () -> meetVerificationService.getMeetVerification(1L, 100L));
    }

    @Test
    @DisplayName("만남 인증 초기화 - 성공")
    void createPendingVerification_Success() {
        meetVerificationService.createPendingVerification(100L);

        verify(meetVerificationRepository).save(any(MeetVerification.class));
    }

    @Test
    @DisplayName("GPS 노쇼 판정 - 대상 없음")
    void judgeGpsNoShow_NoTargets() {
        given(meetVerificationRepository.findAllByStatus(VerificationStatus.PENDING)).willReturn(List.of());

        meetVerificationService.judgeGpsNoShow();

        verify(meetVerificationRepository).findAllByStatus(VerificationStatus.PENDING);
    }

    @Test
    @DisplayName("QR 노쇼 판정 - 대상 없음")
    void judgeQrNoShow_NoTargets() {
        given(meetVerificationRepository.findAllByStatusAndQrExpiresAtBefore(eq(VerificationStatus.VERIFIED), any(LocalDateTime.class)))
                .willReturn(List.of());

        meetVerificationService.judgeQrNoShow();

        verify(meetVerificationRepository).findAllByStatusAndQrExpiresAtBefore(eq(VerificationStatus.VERIFIED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("노쇼 확정 판정 - 대상 없음")
    void judgeNoShowConfirmed_NoTargets() {
        given(meetVerificationRepository.findAllByStatusInAndNoShowDecidedAtBefore(anyList(), any(LocalDateTime.class)))
                .willReturn(List.of());

        meetVerificationService.judgeNoShowConfirmed();

        verify(meetVerificationRepository).findAllByStatusInAndNoShowDecidedAtBefore(anyList(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("게시글 기준 노쇼 확정 - 성공")
    void confirmNoShowByPost_Success() {
        MeetVerification mv = MeetVerification.createPending(100L);
        mv.markApplicantNoShow();
        given(matchService.getMatchIdsByPostId(200L)).willReturn(List.of(100L));
        given(meetVerificationRepository.findAllByMatchIdIn(List.of(100L))).willReturn(List.of(mv));

        meetVerificationService.confirmNoShowByPost(200L);

        assertThat(mv.getStatus()).isEqualTo(VerificationStatus.NO_SHOW_CONFIRMED);
    }

    @Test
    @DisplayName("노쇼 후보 조회 - 성공")
    void getNoShowCandidates_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<MeetVerification> page = new PageImpl<>(List.of(MeetVerification.createPending(100L)));
        given(meetVerificationRepository.findAllByStatusIn(anyList(), eq(pageable))).willReturn(page);

        Page<MeetVerification> result = meetVerificationService.getNoShowCandidates(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("연장 상태 조회 - 성공")
    void getMeetExtension_Success() {
        Long userId = 1L;
        Long matchId = 100L;
        Long postId = 200L;
        MeetVerification mv = MeetVerification.createPending(matchId);
        MatchInfoDto matchInfo = new MatchInfoDto(matchId, postId, userId, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(postId, 2L, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusMinutes(10));
        given(meetVerificationRepository.findByMatchId(matchId)).willReturn(Optional.of(mv));
        given(matchService.getMatchInfo(matchId)).willReturn(matchInfo);
        given(postService.getPostInfo(postId)).willReturn(postInfo);

        GetMeetExtensionResponseDto result = meetVerificationService.getMeetExtension(userId, matchId);

        assertThat(result.matchId()).isEqualTo(matchId);
    }

    @Test
    @DisplayName("연장 요청 만료 처리 - 대상 없음")
    void expireTimeoutExtensions_NoTargets() {
        given(meetVerificationRepository.findAllByExtensionStatusAndExtensionRequestedAtBefore(eq(ExtensionStatus.REQUESTED), any(LocalDateTime.class)))
                .willReturn(List.of());

        meetVerificationService.expireTimeoutExtensions();

        verify(meetVerificationRepository).findAllByExtensionStatusAndExtensionRequestedAtBefore(eq(ExtensionStatus.REQUESTED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("사용자 노쇼 매칭 목록 조회 - 성공")
    void getNoShowMatchesForUser_Success() {
        MeetVerification mv = MeetVerification.createPending(100L);
        mv.confirmNoShowByAdmin();
        given(matchService.getAllMatchIdsByUserId(1L)).willReturn(List.of(100L));
        given(meetVerificationRepository.findAllByMatchIdInAndStatusIn(eq(List.of(100L)), anyList()))
                .willReturn(List.of(mv));

        List<NoShowMatchResponseDto> result = meetVerificationService.getNoShowMatchesForUser(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("매칭 ID로 만남 인증 조회 - 성공")
    void getByMatchId_Success() {
        MeetVerification mv = MeetVerification.createPending(100L);
        given(meetVerificationRepository.findByMatchId(100L)).willReturn(Optional.of(mv));

        MeetVerification result = meetVerificationService.getByMatchId(100L);

        assertThat(result).isSameAs(mv);
    }
}
