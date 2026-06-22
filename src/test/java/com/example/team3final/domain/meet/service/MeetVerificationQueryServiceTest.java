package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.context.MeetVerificationContext;
import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetQrSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetVerificationQueryService 단위 테스트")
class MeetVerificationQueryServiceTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private MeetVerificationContextReader contextReader;

    @Mock
    private MeetQrSupport meetQrSupport;

    @InjectMocks
    private MeetVerificationQueryServiceImpl meetVerificationQueryService;

    @Test
    @DisplayName("만남 연장 상태를 조회하면 참여자 검증 후 연장 상태를 반환한다")
    void getMeetExtension_shouldReturnExtensionStatus() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        MatchInfoDto matchInfo = new MatchInfoDto(10L, 20L, 2L, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(20L, 1L, BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), LocalDateTime.now().plusDays(1));
        when(contextReader.loadMeetContext(10L)).thenReturn(new MeetVerificationContext(meetVerification, matchInfo, postInfo));

        GetMeetExtensionResponseDto response = meetVerificationQueryService.getMeetExtension(2L, 10L);

        assertThat(response.matchId()).isEqualTo(10L);
        verify(contextReader).validateParticipant(2L, matchInfo, postInfo);
    }
}
