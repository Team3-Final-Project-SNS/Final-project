package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchQueryService 단위 테스트")
class MatchQueryServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private MeetVerificationInternalService meetVerificationInternalService;

    @InjectMocks
    private MatchQueryServiceImpl matchQueryService;

    @Test
    @DisplayName("내 매칭 목록을 조회하면 게시글과 사용자 정보를 조합해 페이지 응답을 반환한다")
    void getMatches_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        Match match = match();
        PostMatchInfoDto postInfo = new PostMatchInfoDto(20L, 1L, PostStatus.MATCHED, LocalDateTime.now().plusDays(1), "정문", 300, 2, 2);
        when(matchRepository.findAllByUserIdAndStatus(2L, MatchStatus.MATCHED, pageable))
                .thenReturn(new PageImpl<>(List.of(match), pageable, 1));
        when(postInternalService.getPostMatchInfos(List.of(20L))).thenReturn(Map.of(20L, postInfo));
        when(matchRepository.findAllByPostIdIn(List.of(20L))).thenReturn(List.of(match));
        when(userInternalService.getUserInfos(List.of(1L, 2L))).thenReturn(Map.of(
                1L, userInfo(1L, "작성자"),
                2L, userInfo(2L, "신청자")
        ));
        when(chatInternalService.getChatRoomIdsByPostIds(List.of(20L))).thenReturn(Map.of(20L, 30L));
        when(meetVerificationInternalService.findExtendedMeetAtMapByMatchIds(List.of(10L))).thenReturn(Map.of());

        PageResponseDto<?> response = matchQueryService.getMatches(2L, MatchStatus.MATCHED, pageable);

        assertThat(response.totalElements()).isEqualTo(1);
    }

    private Match match() {
        Match match = Match.builder()
                .postId(20L)
                .applicantId(2L)
                .applicantDeposit(200)
                .build();
        ReflectionTestUtils.setField(match, "id", 10L);
        return match;
    }

    private UserInfoDto userInfo(Long id, String nickname) {
        return new UserInfoDto(id, nickname, "컴퓨터공학", "20", BigDecimal.valueOf(36.5), 1L);
    }
}
