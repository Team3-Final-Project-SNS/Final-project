package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetVerificationServiceImplTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;
    @Mock
    private ChatService chatService;
    @Mock
    private UserLocationService userLocationService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private MeetVerificationServiceImpl meetVerificationService;

    @Test
    @DisplayName("장소 인증 - 성공")
    void createPlaceVerification_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchInfo.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);

        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.authorId()).willReturn(100L);
        given(postInfo.meetAt()).willReturn(LocalDateTime.now());
        given(postInfo.placeLat()).willReturn(new BigDecimal("37.5665"));
        given(postInfo.placeLng()).willReturn(new BigDecimal("126.9780"));
        given(postService.getPostInfo(10L)).willReturn(postInfo);

        PlaceVerificationRequestDto request = new PlaceVerificationRequestDto();
        ReflectionTestUtils.setField(request, "currentLat", new BigDecimal("37.5665"));
        ReflectionTestUtils.setField(request, "currentLng", new BigDecimal("126.9780"));

        // when
        PlaceVerificationResponseDto result = meetVerificationService.createPlaceVerification(100L, 1L, request);

        // then
        assertNotNull(result);
        verify(meetVerification).verifyAuthorPlace();
    }

    @Test
    @DisplayName("QR 발급 - 성공")
    void getMeetQr_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getStatus()).willReturn(VerificationStatus.VERIFIED);
        given(meetVerification.getAuthorPlaceVerifiedAt()).willReturn(LocalDateTime.now());
        given(meetVerification.getApplicantPlaceVerifiedAt()).willReturn(LocalDateTime.now());
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);

        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(postInfo);

        // when
        QrResponseDto result = meetVerificationService.getMeetQr(100L, 1L);

        // then
        assertNotNull(result);
        verify(meetVerification).issueQrToken(anyString(), any());
    }

    @Test
    @DisplayName("QR 스캔 - 성공")
    void createQrScan_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getStatus()).willReturn(VerificationStatus.VERIFIED);
        given(meetVerification.getQrToken()).willReturn("test_qr");
        given(meetVerification.isQrExpired()).willReturn(false);
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.isApplicant(200L)).willReturn(true);
        given(matchInfo.postId()).willReturn(10L);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);

        Match match = mock(Match.class);
        given(match.getApplicantDeposit()).willReturn(500);
        given(matchService.getMatchById(1L)).willReturn(match);

        QrScanRequestDto request = new QrScanRequestDto();
        ReflectionTestUtils.setField(request, "qrToken", "test_qr");

        // when
        QrScanResponseDto result = meetVerificationService.createQrScan(200L, 1L, request);

        // then
        assertNotNull(result);
        assertEquals(500, result.refundedPoint());
        verify(meetVerification).meetVerifiedDone();
        verify(matchService).completeMatch(1L);
    }

    @Test
    @DisplayName("만남 시간 연장 요청 - 성공")
    void createMeetExtension_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getExtensionStatus()).willReturn(com.example.team3final.domain.meet.enums.ExtensionStatus.NONE);
        given(meetVerification.isExtended()).willReturn(false);
        given(meetVerification.getExtensionRequestedAt()).willReturn(LocalDateTime.now());
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.meetAt()).willReturn(LocalDateTime.now().plusHours(1));
        given(postService.getPostInfo(anyLong())).willReturn(postInfo);
        given(matchInfo.isParticipant(anyLong(), anyLong())).willReturn(true);
        given(matchInfo.status()).willReturn(com.example.team3final.domain.match.enums.MatchStatus.MATCHED);

        com.example.team3final.domain.user.dto.response.UserInfoDto userInfo = new com.example.team3final.domain.user.dto.response.UserInfoDto(100L, "nick", "major", "2024", 1L);
        given(userService.getUserInfo(100L)).willReturn(userInfo);

        // when
        CreateMeetExtensionResponseDto result = meetVerificationService.createMeetExtension(100L, 1L);

        // then
        assertNotNull(result);
        verify(meetVerification).requestExtension(100L);
    }

    @Test
    @DisplayName("만남 시간 연장 수락 - 성공")
    void acceptMeetExtension_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getExtensionStatus()).willReturn(com.example.team3final.domain.meet.enums.ExtensionStatus.REQUESTED);
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.meetAt()).willReturn(LocalDateTime.now().plusHours(1));
        given(postService.getPostInfo(anyLong())).willReturn(postInfo);
        given(matchInfo.isParticipant(anyLong(), anyLong())).willReturn(true);

        // when
        AcceptMeetExtensionResponseDto result = meetVerificationService.acceptMeetExtension(100L, 1L);

        // then
        assertNotNull(result);
        verify(meetVerification).acceptExtension(any(LocalDateTime.class), eq(15L));
    }

    @Test
    @DisplayName("만남 시간 연장 거절 - 성공")
    void rejectMeetExtension_Success() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getExtensionStatus()).willReturn(com.example.team3final.domain.meet.enums.ExtensionStatus.REQUESTED);
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postService.getPostInfo(anyLong())).willReturn(postInfo);
        given(matchInfo.isParticipant(anyLong(), anyLong())).willReturn(true);

        // when
        RejectMeetExtensionResponseDto result = meetVerificationService.rejectMeetExtension(100L, 1L);

        // then
        assertNotNull(result);
        verify(meetVerification).rejectExtension();
    }

    @Test
    @DisplayName("만남 시간 연장 상태 조회 - 성공 (요청자 없음)")
    void getMeetExtension_Success_NoRequester() {
        // given
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getExtensionRequesterId()).willReturn(null); // No requester
        given(meetVerificationRepository.findByMatchId(1L)).willReturn(Optional.of(meetVerification));

        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.meetAt()).willReturn(LocalDateTime.now().plusHours(1));
        given(postService.getPostInfo(10L)).willReturn(postInfo);
        given(matchInfo.isParticipant(anyLong(), anyLong())).willReturn(true);

        // when
        GetMeetExtensionResponseDto result = meetVerificationService.getMeetExtension(100L, 1L);

        // then
        assertNotNull(result);
        assertNull(result.requesterNickname());
    }
    }
