package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @InjectMocks
    private MatchServiceImpl matchService;

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchCreateService matchCreateService;
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
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserLocationCleanupService userLocationCleanupService;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("매칭 정보 조회 - 성공")
    void getMatchInfo_Success() {
        // given
        Long matchId = 1L;
        com.example.team3final.domain.match.entity.Match match = mock(com.example.team3final.domain.match.entity.Match.class);
        given(match.getId()).willReturn(matchId);
        given(matchRepository.findById(matchId)).willReturn(Optional.of(match));

        // when
        MatchInfoDto result = matchService.getMatchInfo(matchId);

        // then
        assertThat(result.matchId()).isEqualTo(matchId);
    }

    @Test
    @DisplayName("매칭 생성 - 성공")
    void createMatch_Success() {
        CreateMatchResponseDto response = mock(CreateMatchResponseDto.class);
        given(matchCreateService.createMatch(100L, 2L)).willReturn(response);

        CreateMatchResponseDto result = matchService.createMatch(100L, 2L);

        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("게시글 신청 여부 조회 - 성공")
    void hasAppliedToPost_Success() {
        given(matchRepository.existsByPostIdAndApplicantIdAndStatus(100L, 2L, MatchStatus.MATCHED)).willReturn(true);

        boolean result = matchService.hasAppliedToPost(100L, 2L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("게시글 매칭 ID 목록 조회 - 성공")
    void getMatchIdsByPostId_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findAllByPostId(100L)).willReturn(List.of(match));

        List<Long> result = matchService.getMatchIdsByPostId(100L);

        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("게시글 완료 매칭 목록 조회 - 성공")
    void getCompletedMatchesByPostId_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findAllByPostIdAndStatus(100L, MatchStatus.COMPLETED)).willReturn(List.of(match));

        List<Match> result = matchService.getCompletedMatchesByPostId(100L);

        assertThat(result).containsExactly(match);
    }

    @Test
    @DisplayName("완료 매칭 단건 조회 - 성공")
    void findCompletedMatchById_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        match.complete();
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));

        Optional<Match> result = matchService.findCompletedMatchById(1L);

        assertThat(result).contains(match);
    }

    @Test
    @DisplayName("분쟁 상태 표시 - 성공")
    void markDisputed_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));

        matchService.markDisputed(1L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.DISPUTED);
    }

    @Test
    @DisplayName("시스템 매칭 취소 - 성공")
    void cancelMatchBySystem_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        Post post = createPost(100L, 1L, PostStatus.MATCHED);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));
        given(postService.getPostById(100L)).willReturn(post);

        matchService.cancelMatchBySystem(1L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(post.getStatus()).isEqualTo(PostStatus.CANCELLED);
        verify(chatService).deactivateChatRoom(100L);
    }

    @Test
    @DisplayName("분쟁으로 단건 매칭 완료 - 이미 처리된 상태")
    void completeSingleMatchByDispute_AlreadyResolved() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        match.cancel();
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));

        int result = matchService.completeSingleMatchByDispute(1L, 2L);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("분쟁으로 노쇼 처리 - 이미 처리된 상태")
    void markNoShowByDispute_AlreadyResolved() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        match.cancel();
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));

        int result = matchService.markNoShowByDispute(1L, VerificationStatus.GUEST_NO_SHOW, 2L);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("등록자 노쇼 처리 - 성공")
    void markAuthorNoShow_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        Post post = createPost(100L, 1L, PostStatus.MATCHED);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));
        given(postService.getPostById(100L)).willReturn(post);

        matchService.markAuthorNoShow(1L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.AUTHOR_NO_SHOW);
        verify(userPointService).penaltyPoint(1L, 1000, 1L);
    }

    @Test
    @DisplayName("신청자 노쇼 처리 - 성공")
    void markApplicantNoShow_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        Post post = createPost(100L, 1L, PostStatus.MATCHED);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));
        given(postService.getPostById(100L)).willReturn(post);

        matchService.markApplicantNoShow(1L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.APPLICANT_NO_SHOW);
        verify(userPointService).penaltyPoint(2L, 1000, 1L);
    }

    @Test
    @DisplayName("양측 노쇼 처리 - 성공")
    void markBothNoShow_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        Post post = createPost(100L, 1L, PostStatus.MATCHED);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));
        given(postService.getPostById(100L)).willReturn(post);

        matchService.markBothNoShow(1L);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.BOTH_NO_SHOW);
    }

    @Test
    @DisplayName("매칭 취소 - 상태 오류")
    void cancelMatch_InvalidStatus_ThrowsException() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        match.cancel(); // status = CANCELLED
        given(matchRepository.findByIdWithLock(1L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.cancelMatch(1L, 2L, new CancelMatchRequestDto("reason")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("매칭 엔티티 조회 - 성공")
    void getMatchById_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findById(1L)).willReturn(Optional.of(match));

        Match result = matchService.getMatchById(1L);

        assertThat(result).isSameAs(match);
    }

    @Test
    @DisplayName("매칭 목록 조회 - 성공")
    void getMatches_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Match match = createMatch(1L, 100L, 2L, 1000);
        PostMatchInfoDto postInfo = new PostMatchInfoDto(100L, 1L, LocalDateTime.now().plusDays(1), "place", 1000, 1, 2);
        given(matchRepository.findAllByUserId(1L, pageable)).willReturn(new PageImpl<>(List.of(match), pageable, 1));
        given(postService.getPostMatchInfos(List.of(100L))).willReturn(Map.of(100L, postInfo));
        given(userService.getUserInfos(List.of(2L))).willReturn(Map.of(2L, userInfo(2L)));
        given(chatService.getChatRoomIdsByPostIds(List.of(100L))).willReturn(Map.of(100L, 10L));

        PageResponseDto<GetMatchesResponseDto> result = matchService.getMatches(1L, null, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("매칭 정보 맵 조회 - 성공")
    void getMatchInfos_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findAllById(List.of(1L))).willReturn(List.of(match));

        Map<Long, MatchInfoDto> result = matchService.getMatchInfos(List.of(1L));

        assertThat(result).containsKey(1L);
    }

    @Test
    @DisplayName("단건 매칭 완료 - 성공")
    void completeSingleMatch_Success() {
        Match match = createMatch(1L, 100L, 2L, 1000);
        given(matchRepository.findByIdWithLock(1L)).willReturn(Optional.of(match));
        given(matchRepository.countByPostIdAndStatus(100L, MatchStatus.MATCHED)).willReturn(0L);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        boolean result = matchService.completeSingleMatch(1L);

        assertThat(result).isTrue();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("전체 매칭 완료 시 게시글 완료 - 성공")
    void completePostIfAllMatchesCompleted_Success() {
        Post post = createPost(100L, 1L, PostStatus.MATCHED);
        given(matchRepository.countByPostIdAndStatus(100L, MatchStatus.MATCHED)).willReturn(0L);
        given(postService.getPostByIdWithLock(100L)).willReturn(post);

        matchService.completePostIfAllMatchesCompleted(100L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
        verify(userPointService).refundPoint(1L, 1000, 100L);
    }

    @Test
    @DisplayName("사용자 전체 매칭 ID 조회 - 성공")
    void getAllMatchIdsByUserId_Success() {
        given(matchRepository.findAllMatchIdsByUserId(1L)).willReturn(List.of(1L, 2L));

        List<Long> result = matchService.getAllMatchIdsByUserId(1L);

        assertThat(result).containsExactly(1L, 2L);
    }

    private Match createMatch(Long id, Long postId, Long applicantId, int deposit) {
        Match match = Match.builder()
                .postId(postId)
                .applicantId(applicantId)
                .applicantDeposit(deposit)
                .build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }

    private Post createPost(Long id, Long authorId, PostStatus status) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("place")
                .placeLat(new BigDecimal("37.0"))
                .placeLng(new BigDecimal("127.0"))
                .content("content")
                .authorDeposit(1000)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        post.changeStatus(status);
        return post;
    }

    private UserInfoDto userInfo(Long userId) {
        return new UserInfoDto(userId, "nickname" + userId, "major", "24", new BigDecimal("36.5"), 1L);
    }
}
