package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CancelMatchResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private UserService userService;

    @Mock
    private PostService postService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private MatchServiceImpl matchService;

    @Test
    @DisplayName("매칭 생성 - 성공")
    void createMatch_Success() {
        // given
        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(1L);
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getAuthorDeposit()).willReturn(500);
        given(post.isFull()).willReturn(false); // Fix: not full yet
        given(postService.getPostById(100L)).willReturn(post);

        Match match = mock(Match.class);
        given(match.getId()).willReturn(200L);
        given(matchRepository.save(any(Match.class))).willReturn(match);

        UserInfoDto userInfo = mock(UserInfoDto.class);
        given(userInfo.nickname()).willReturn("Nick");
        given(userService.getUserInfo(anyLong())).willReturn(userInfo);

        // when
        CreateMatchResponseDto result = matchService.createMatch(100L, 2L);

        // then
        assertNotNull(result);
        verify(userPointService).deductPoint(2L, 500, null);
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    @DisplayName("매칭 생성 - 실패 (본인 게시글)")
    void createMatch_Fail_SelfApply() {
        // given
        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(1L);
        given(postService.getPostById(100L)).willReturn(post);

        // when & then
        MatchException exception = assertThrows(MatchException.class, () -> matchService.createMatch(100L, 1L));
        assertEquals(ErrorCode.MATCH_SELF_APPLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("매칭 완료 - 성공")
    void completeMatch_Success() {
        // given
        Match match = mock(Match.class);
        given(match.getStatus()).willReturn(MatchStatus.MATCHED);
        given(match.getPostId()).willReturn(100L);
        given(match.getApplicantId()).willReturn(2L);
        given(match.getApplicantDeposit()).willReturn(500);
        given(matchRepository.findById(200L)).willReturn(Optional.of(match));

        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(1L);
        given(post.getAuthorDeposit()).willReturn(500);
        given(postService.getPostById(100L)).willReturn(post);

        // when
        matchService.completeMatch(200L);

        // then
        verify(match).complete();
        verify(postService).completePost(100L);
        verify(userPointService).refundPoint(1L, 500, 200L);
        verify(userPointService).refundPoint(2L, 500, 200L);
    }

    @Test
    @DisplayName("매칭 취소 - 성공")
    void cancelMatch_Success() {
        // given
        Match match = mock(Match.class);
        given(match.getId()).willReturn(200L);
        given(match.getStatus()).willReturn(MatchStatus.MATCHED);
        given(match.getPostId()).willReturn(100L);
        given(match.isParticipant(1L, 1L)).willReturn(true);
        given(match.isApplicant(1L)).willReturn(false);
        given(matchRepository.findById(200L)).willReturn(Optional.of(match));

        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(1L);
        given(post.getAuthorDeposit()).willReturn(500);
        given(post.getMeetAt()).willReturn(LocalDateTime.now().plusHours(1));
        given(postService.getPostById(100L)).willReturn(post);

        // when
        CancelMatchResponseDto result = matchService.cancelMatch(200L, 1L, new CancelMatchRequestDto());

        // then
        assertNotNull(result);
        verify(userPointService).partialRefundPoint(1L, 500, 200L);
        verify(match).cancel();
        verify(post).cancel();
    }
}
