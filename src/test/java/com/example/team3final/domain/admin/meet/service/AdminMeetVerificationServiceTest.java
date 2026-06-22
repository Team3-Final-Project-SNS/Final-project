package com.example.team3final.domain.admin.meet.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.dispute.service.DisputeInternalService;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.service.MeetVerificationNoShowService;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 만남 인증 서비스 단위 테스트")
class AdminMeetVerificationServiceTest {

    @Mock
    private MeetVerificationNoShowService meetVerificationNoShowService;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private DisputeInternalService disputeInternalService;

    @InjectMocks
    private AdminMeetVerificationServiceImpl adminMeetVerificationService;

    @Test
    @DisplayName("노쇼 후보 조회는 노쇼 서비스의 페이지 결과를 관리자 응답 페이지로 변환한다")
    void getNoShowCandidates_shouldReturnEmptyPageWhenNoCandidates() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(meetVerificationNoShowService.getNoShowCandidates(pageable))
                .thenReturn(new PageImpl<MeetVerification>(List.of(), pageable, 0));

        PageResponseDto<AdminNoShowCandidateResponseDto> result =
                adminMeetVerificationService.getNoShowCandidates(pageable);

        assertThat(result.content()).isEmpty();
        verify(meetVerificationNoShowService).getNoShowCandidates(pageable);
    }
}
