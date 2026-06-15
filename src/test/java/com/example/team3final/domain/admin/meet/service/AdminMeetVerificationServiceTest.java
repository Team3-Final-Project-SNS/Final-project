package com.example.team3final.domain.admin.meet.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMeetVerificationServiceTest {

    @InjectMocks
    private AdminMeetVerificationServiceImpl adminMeetVerificationService;

    @Mock
    private MeetVerificationService meetVerificationService;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;
    @Mock
    private UserService userService;
    @Mock
    private DisputeService disputeService;

    @Test
    @DisplayName("노쇼 후보군 조회 - 성공 (빈 목록)")
    void getNoShowCandidates_Empty_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        given(meetVerificationService.getNoShowCandidates(pageable)).willReturn(new PageImpl<>(List.of()));

        // when
        PageResponseDto<AdminNoShowCandidateResponseDto> result = adminMeetVerificationService.getNoShowCandidates(pageable);

        // then
        assertThat(result.content()).isEmpty();
    }
}
