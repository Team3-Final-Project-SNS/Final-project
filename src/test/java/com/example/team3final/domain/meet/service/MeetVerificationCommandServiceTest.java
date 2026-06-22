package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchLifecycleService;
import com.example.team3final.domain.meet.context.MeetVerificationContext;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetExtensionSupport;
import com.example.team3final.domain.meet.service.support.MeetQrSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetVerificationCommandService 단위 테스트")
class MeetVerificationCommandServiceTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private MatchLifecycleService matchLifecycleService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private UserLocationCleanupService userLocationCleanupService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private MeetVerificationContextReader contextReader;

    @Mock
    private MeetExtensionSupport meetExtensionSupport;

    @Mock
    private MeetQrSupport meetQrSupport;

    @Mock
    private MeetOverdueReservationService meetOverdueReservationService;

    @InjectMocks
    private MeetVerificationCommandServiceImpl meetVerificationCommandService;

    @Test
    @DisplayName("만남 연장 요청은 신청자만 가능하므로 작성자가 요청하면 실패한다")
    void createMeetExtension_shouldThrowWhenRequesterIsAuthor() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        MatchInfoDto matchInfo = new MatchInfoDto(10L, 20L, 2L, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(20L, 1L, BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1), LocalDateTime.now().plusDays(1));
        when(contextReader.loadMeetContext(10L)).thenReturn(new MeetVerificationContext(meetVerification, matchInfo, postInfo));

        assertThatThrownBy(() -> meetVerificationCommandService.createMeetExtension(1L, 10L))
                .isInstanceOf(MeetException.class);
    }
}
