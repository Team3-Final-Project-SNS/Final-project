package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.*;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    // ====================================================================
    // 테스트 대상(SUT: System Under Test) + Mock 의존성 선언
    //
    // @InjectMocks: PostServiceImpl 인스턴스를 만들고 아래 @Mock들을 생성자 주입
    // @Mock: 실제 구현 없이 동작을 지정할 수 있는 가짜 객체
    // ====================================================================
    @InjectMocks
    private PostServiceImpl postService;

    @Mock private PostRepository postRepository;
    @Mock private UserService userService;
    @Mock private UserPointService userPointService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private ReviewAvoidanceService reviewAvoidanceService;
    @Mock private RedisPostService redisPostService;

    // ====================================================================
    // createPost — 게시글 생성
    // ====================================================================

    @Test
    @DisplayName("게시글 생성 - 성공")
    void createPost_Success() {
        // given
        Long authorId = 1L;
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))  // 미래 시간 → 검증 통과
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("같이 밥 먹어요")
                .authorDeposit(500)   // 200P 이상 + 100P 단위 → 검증 통과
                .maxApplicants(2)
                .build();

        // PostServiceImpl.createPost() 내부에서 작성자 닉네임 조회에 사용
        given(userService.getUserInfo(authorId)).willReturn(userInfo(1L, 10L));

        Post saved = buildPost(100L, authorId, PostStatus.OPEN, 500);
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        CreatePostResponseDto result = postService.createPost(authorId, request);

        // then
        assertThat(result.postId()).isEqualTo(100L);

        // 포인트 차감이 정확한 금액으로 호출되었는지 검증
        // deductPoint(userId, amount, matchId) — matchId는 게시글 생성 시 null
        verify(userPointService).deductPoint(eq(authorId), eq(500), isNull());
    }

    @Test
    @DisplayName("게시글 생성 - 과거 시간 차단")
    void createPost_PastMeetAt_ThrowsException() {
        // given: 1시간 전 시간 → PostServiceImpl 첫 번째 검증에서 예외 발생
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().minusHours(1))
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("테스트")
                .authorDeposit(500)
                .maxApplicants(2)
                .build();

        // when & then
        assertThatThrownBy(() -> postService.createPost(1L, request))
                .isInstanceOf(PostException.class);

        // 예외가 포인트 차감 전에 발생했으므로 userPointService는 절대 호출되면 안 됨
        verifyNoInteractions(userPointService);
    }

    @Test
    @DisplayName("게시글 생성 - 최소 책임비(200P) 미달 차단")
    void createPost_BelowMinDeposit_ThrowsException() {
        // given: 100P → MIN_AUTHOR_DEPOSIT(200) 미달
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("테스트")
                .authorDeposit(100)  // 200P 미만
                .maxApplicants(2)
                .build();

        assertThatThrownBy(() -> postService.createPost(1L, request))
                .isInstanceOf(PostException.class);

        verifyNoInteractions(userPointService);
    }

    @Test
    @DisplayName("게시글 생성 - 100P 단위 위반 차단")
    void createPost_InvalidDepositUnit_ThrowsException() {
        // given: 350P → 350 % 100 = 50 ≠ 0 → 단위 위반
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("테스트")
                .authorDeposit(350)  // 100P 단위 아님
                .maxApplicants(2)
                .build();

        assertThatThrownBy(() -> postService.createPost(1L, request))
                .isInstanceOf(PostException.class);

        verifyNoInteractions(userPointService);
    }

    // ====================================================================
    // updatePost — 게시글 수정
    //
    // ⚠️ 주의: PostServiceImpl.updatePost() 안에서 포인트 차감 로직을 직접
    //   확인한 뒤 아래 verify를 맞춰야 함.
    //   deposit이 올라가면 차액 차감, 내려가면 차액 환불, 같으면 포인트 변동 없음.
    // ====================================================================

    @Test
    @DisplayName("게시글 수정 - 성공 (책임비 증액, 차액 차감)")
    void updatePost_Success_DepositIncreased() {
        // given: 기존 500P → 700P 로 증액 → 차액 200P 추가 차감
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "후문",
                new BigDecimal("37.1"),
                new BigDecimal("127.1"),
                "내용 변경",
                700  // 500 → 700: 200P 증액
        );

        // when
        UpdatePostResponseDto result = postService.updatePost(100L, 1L, request);

        // then
        assertThat(result.postId()).isEqualTo(100L);
        // 실제 PostServiceImpl의 포인트 차감 메서드명으로 맞춰야 함
        // 증액 시 차액(200P) 차감 — 메서드명은 구현 코드 확인 필요
        verify(userPointService).deductEditDeposit(eq(1L), eq(200));
    }

    @Test
    @DisplayName("게시글 수정 - 성공 (책임비 감액, 차액 환불)")
    void updatePost_Success_DepositDecreased() {
        // given: 기존 700P → 500P 로 감액 → 차액 200P 환불
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 700);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "후문",
                new BigDecimal("37.1"),
                new BigDecimal("127.1"),
                "내용 변경",
                500  // 700 → 500: 200P 감액
        );

        // when
        UpdatePostResponseDto result = postService.updatePost(100L, 1L, request);

        // then
        assertThat(result.postId()).isEqualTo(100L);
        // 감액 시 차액 환불 — 실제 메서드명 확인 필요
        verify(userPointService).refundEditDeposit(eq(1L), eq(200));
    }

    @Test
    @DisplayName("게시글 수정 - 타인 수정 차단")
    void updatePost_NotAuthor_ThrowsException() {
        // given: authorId=1L 게시글을 userId=2L 이 수정 시도
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "후문", new BigDecimal("37.1"), new BigDecimal("127.1"), "변경", 500
        );

        // userId=2L → post.isAuthor(2L) = false → 예외
        assertThatThrownBy(() -> postService.updatePost(100L, 2L, request))
                .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("게시글 수정 - MATCHED 상태 수정 차단")
    void updatePost_MatchedStatus_ThrowsException() {
        Post post = buildPost(100L, 1L, PostStatus.MATCHED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "후문", new BigDecimal("37.1"), new BigDecimal("127.1"), "변경", 500
        );

        assertThatThrownBy(() -> postService.updatePost(100L, 1L, request))
                .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("게시글 수정 - COMPLETED 상태 수정 차단")
    void updatePost_CompletedStatus_ThrowsException() {
        Post post = buildPost(100L, 1L, PostStatus.COMPLETED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "후문", new BigDecimal("37.1"), new BigDecimal("127.1"), "변경", 500
        );

        assertThatThrownBy(() -> postService.updatePost(100L, 1L, request))
                .isInstanceOf(PostException.class);
    }

    // ====================================================================
    // deletePost — 게시글 삭제
    // ====================================================================

    @Test
    @DisplayName("게시글 삭제 - 성공")
    void deletePost_Success() {
        // given: OPEN 상태 + 본인(1L) → 삭제 가능
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when
        DeletePostResponseDto result = postService.deletePost(100L, 1L);

        // then
        assertThat(result.postId()).isEqualTo(100L);
        // 삭제 시 예치금 전액 환불 — refundPoint(userId, amount, matchId=null)
        verify(userPointService).refundPoint(eq(1L), eq(500), isNull());
    }

    @Test
    @DisplayName("게시글 삭제 - 타인 삭제 차단")
    void deletePost_NotAuthor_ThrowsException() {
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // userId=2L → 본인 아님
        assertThatThrownBy(() -> postService.deletePost(100L, 2L))
                .isInstanceOf(PostException.class);

        verifyNoInteractions(userPointService);
    }

    @Test
    @DisplayName("게시글 삭제 - MATCHED 상태 삭제 차단")
    void deletePost_MatchedStatus_ThrowsException() {
        Post post = buildPost(100L, 1L, PostStatus.MATCHED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(100L, 1L))
                .isInstanceOf(PostException.class);

        verifyNoInteractions(userPointService);
    }

    @Test
    @DisplayName("게시글 삭제 - COMPLETED 상태 삭제 차단")
    void deletePost_CompletedStatus_ThrowsException() {
        Post post = buildPost(100L, 1L, PostStatus.COMPLETED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(100L, 1L))
                .isInstanceOf(PostException.class);

        verifyNoInteractions(userPointService);
    }

    // ====================================================================
    // completePost — 게시글 완료 처리
    // ====================================================================

    @Test
    @DisplayName("게시글 완료 - 성공 (MATCHED → COMPLETED)")
    void completePost_Success() {
        // given: MATCHED 상태여야 complete() 호출 가능
        Post post = buildPost(100L, 1L, PostStatus.MATCHED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when
        postService.completePost(100L);

        // then
        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("게시글 완료 - 이미 COMPLETED 멱등성 처리")
    void completePost_AlreadyCompleted_Idempotent() {
        // given: 이미 COMPLETED → completePost() 두 번 호출해도 예외 없이 스킵
        Post post = buildPost(100L, 1L, PostStatus.COMPLETED, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when: 예외 없이 정상 종료되어야 함
        assertThatCode(() -> postService.completePost(100L))
                .doesNotThrowAnyException();

        // 상태 변화 없이 COMPLETED 유지
        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    // ====================================================================
    // getPostById — 단건 조회
    // ====================================================================

    @Test
    @DisplayName("게시글 단건 조회 - 성공")
    void getPostById_Success() {
        Post post = buildPost(100L, 1L, PostStatus.OPEN, 500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostById(100L);

        // isSameAs: 같은 객체 참조인지 확인 (equals가 아닌 == 비교)
        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("게시글 단건 조회 - 존재하지 않는 ID 차단")
    void getPostById_NotFound_ThrowsException() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(999L))
                .isInstanceOf(PostException.class);
    }

    // ====================================================================
    // getPosts — 게시글 목록 조회
    // ====================================================================

    @Test
    @DisplayName("게시글 목록 조회 - 성공 (캐시 미스 → DB 조회)")
    void getPosts_Success() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Post post = buildPost(100L, 2L, PostStatus.OPEN, 500);

        // Redis 캐시에 데이터 없음 → DB 조회로 이어짐
        given(redisPostService.getPostList(1L, PostStatus.OPEN, 0, 10))
                .willReturn(Optional.empty());

        // 현재 유저(1L)의 학교 ID 조회
        given(userService.getUserInfo(1L)).willReturn(userInfo(1L, 10L));

        // 같은 학교 유저 목록
        given(userService.getUserIdsByUniversityId(10L)).willReturn(List.of(2L));

        // 차단 관계 없음
        given(reviewAvoidanceService.getAvoidedUserIds(1L)).willReturn(List.of());

        // DB에서 게시글 조회
        given(postRepository.findByAuthorIdInAndStatus(List.of(2L), PostStatus.OPEN, pageable))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));

        // 작성자 정보 bulk 조회
        given(userService.getUserInfos(List.of(2L)))
                .willReturn(Map.of(2L, userInfo(2L, 10L)));

        // when
        PageResponseDto<GetPostsItemResponseDto> result =
                postService.getPosts(1L, PostStatus.OPEN, pageable);

        // then
        assertThat(result.content()).hasSize(1);

        // 조회 결과를 Redis에 저장했는지 확인
        verify(redisPostService).savePostList(eq(1L), eq(PostStatus.OPEN), eq(0), eq(10), any());
    }

    @Test
    @DisplayName("게시글 목록 조회 - 캐시 히트 시 DB 조회 없이 반환")
    void getPosts_CacheHit_NoDatabaseCall() {
        // given: Redis 캐시에 데이터 있음
        PageRequest pageable = PageRequest.of(0, 10);
        PageResponseDto<GetPostsItemResponseDto> cached =
                new PageResponseDto<>(List.of(), 0, 10, 0L, 0, true);

        given(redisPostService.getPostList(1L, PostStatus.OPEN, 0, 10))
                .willReturn(Optional.of(cached));

        // when
        PageResponseDto<GetPostsItemResponseDto> result =
                postService.getPosts(1L, PostStatus.OPEN, pageable);

        // then: 캐시 반환 → DB, UserService 절대 호출 안 됨
        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(postRepository);
        verifyNoInteractions(userService);
    }

    // ====================================================================
    // 헬퍼 메서드
    //
    // buildPost: 테스트마다 필요한 Post 객체를 일관되게 생성
    //   - id는 DB 생성값이므로 빌더에 없음 → ReflectionTestUtils로 강제 주입
    //   - status는 빌더에서 항상 OPEN → 다른 상태가 필요하면 changeStatus() 호출
    // ====================================================================
    private Post buildPost(Long id, Long authorId, PostStatus status, int deposit) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("테스트 게시글")
                .authorDeposit(deposit)
                .maxApplicants(2)
                .build();

        // private 필드 id에 값 직접 주입 (테스트 전용 유틸)
        ReflectionTestUtils.setField(post, "id", id);

        // OPEN이 아닌 상태가 필요할 때 도메인 메서드로 전환
        if (status != PostStatus.OPEN) {
            post.changeStatus(status);
        }

        return post;
    }

    // UserInfoDto 생성 헬퍼
    private UserInfoDto userInfo(Long userId, Long universityId) {
        return new UserInfoDto(userId, "nickname" + userId, "major", "24",
                new BigDecimal("36.5"), universityId);
    }
}