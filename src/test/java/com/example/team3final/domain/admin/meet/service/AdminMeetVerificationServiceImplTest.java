package com.example.team3final.domain.admin.meet.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminMeetVerificationServiceImplTest {

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

    @InjectMocks
    private AdminMeetVerificationServiceImpl adminMeetVerificationService;

    @Test
    @DisplayName("노쇼 후보군 조회 - 성공")
    void getNoShowCandidates_Success() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        
        MeetVerification meetVerification = mock(MeetVerification.class);
        given(meetVerification.getMatchId()).willReturn(1L);
        Page<MeetVerification> page = new PageImpl<>(List.of(meetVerification));
        given(meetVerificationService.getNoShowCandidates(any())).willReturn(page);
        
        MatchInfoDto matchInfo = new MatchInfoDto(1L, 10L, 2L, com.example.team3final.domain.match.enums.MatchStatus.MATCHED);
        given(matchService.getMatchInfos(List.of(1L))).willReturn(Map.of(1L, matchInfo));
        
        PostInfoDto postInfo = new PostInfoDto(10L, 2L, new java.math.BigDecimal("37.5"), new java.math.BigDecimal("127.0"), LocalDateTime.now());
        given(postService.getPostInfos(List.of(10L))).willReturn(Map.of(10L, postInfo));
        
        given(userService.getUserNicknameMap(anyList())).willReturn(Map.of(2L, "host", 3L, "guest"));
        given(disputeService.getMatchIdsWithDispute(List.of(1L))).willReturn(Set.of());

        // when
        PageResponseDto<AdminNoShowCandidateResponseDto> result = adminMeetVerificationService.getNoShowCandidates(pageable);

        // then
        assertNotNull(result);
    }
}
