package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.DeletePostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @InjectMocks
    private PostServiceImpl postService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserService userService;
    @Mock
    private UserPointService userPointService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private ReviewAvoidanceService reviewAvoidanceService;
    @Mock
    private RedisPostService redisPostService;

    @Test
    @DisplayName("게시글 생성 - 성공")
    void createPost_Success() {
        // given
        Long authorId = 1L;
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("place")
                .placeLat(new BigDecimal("37.0"))
                .placeLng(new BigDecimal("127.0"))
                .content("content")
                .authorDeposit(1000)
                .maxApplicants(2)
                .build();

        given(userService.getUserInfo(authorId)).willReturn(new UserInfoDto(authorId, "nickname", "major", "123456", new BigDecimal("36.5"), 1L));

        Post post = Post.builder().authorId(authorId).build();
        ReflectionTestUtils.setField(post, "id", 100L);
        given(postRepository.save(any(Post.class))).willReturn(post);

        // when
        CreatePostResponseDto result = postService.createPost(authorId, request);

        // then
        assertThat(result.postId()).isEqualTo(100L);
        verify(userPointService).deductPoint(eq(authorId), eq(1000), any());
    }

    @Test
    @DisplayName("게시글 수정 - 성공")
    void updatePost_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(2),
                "new place",
                new BigDecimal("37.1"),
                new BigDecimal("127.1"),
                "new content",
                1200
        );

        UpdatePostResponseDto result = postService.updatePost(100L, 1L, request);

        assertThat(result.postId()).isEqualTo(100L);
        verify(userPointService).deductEditDeposit(1L, 200);
    }

    @Test
    @DisplayName("게시글 완료 - 성공")
    void completePost_Success() {
        Post post = createPost(100L, 1L, 1000);
        post.changeStatus(PostStatus.MATCHED);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.completePost(100L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("게시글 삭제 - 성공")
    void deletePost_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        DeletePostResponseDto result = postService.deletePost(100L, 1L);

        assertThat(result.postId()).isEqualTo(100L);
        verify(userPointService).refundPoint(1L, 1000, null);
    }

    @Test
    @DisplayName("게시글 목록 조회 - 성공")
    void getPosts_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Post post = createPost(100L, 2L, 1000);
        given(redisPostService.getPostList(1L, PostStatus.OPEN, 0, 10)).willReturn(Optional.empty());
        given(userService.getUserInfo(1L)).willReturn(userInfo(1L, 10L));
        given(userService.getUserIdsByUniversityId(10L)).willReturn(List.of(2L));
        given(reviewAvoidanceService.getAvoidedUserIds(1L)).willReturn(List.of());
        given(postRepository.findByAuthorIdInAndStatus(List.of(2L), PostStatus.OPEN, pageable))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));
        given(userService.getUserInfos(List.of(2L))).willReturn(Map.of(2L, userInfo(2L, 10L)));

        PageResponseDto<GetPostsItemResponseDto> result = postService.getPosts(1L, PostStatus.OPEN, pageable);

        assertThat(result.content()).hasSize(1);
        verify(redisPostService).savePostList(eq(1L), eq(PostStatus.OPEN), eq(0), eq(10), any());
    }

    @Test
    @DisplayName("작성자별 게시글 목록 조회 - 성공")
    void getPostsByAuthor_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByAuthorId(1L, pageable)).willReturn(new PageImpl<>(List.of(post), pageable, 1));
        given(userService.getUserInfos(List.of(1L))).willReturn(Map.of(1L, userInfo(1L, 10L)));

        PageResponseDto<GetPostsItemResponseDto> result = postService.getPostsByAuthor(1L, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("게시글 엔티티 조회 - 성공")
    void getPostById_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostById(100L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("게시글 잠금 조회 - 성공")
    void getPostByIdWithLock_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByIdWithLock(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostByIdWithLock(100L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("게시글 정보 조회 - 성공")
    void getPostInfo_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        PostInfoDto result = postService.getPostInfo(100L);

        assertThat(result.postId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("게시글 상세 조회 - 성공")
    void getPost_Success() {
        Post post = createPost(100L, 2L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userService.getUserInfo(1L)).willReturn(userInfo(1L, 10L));
        given(userService.getUserInfo(2L)).willReturn(userInfo(2L, 10L));

        GetPostResponseDto result = postService.getPost(100L, 1L);

        assertThat(result.postId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("게시글 매칭 정보 조회 - 성공")
    void getPostMatchInfo_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        PostMatchInfoDto result = postService.getPostMatchInfo(100L);

        assertThat(result.postId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("게시글 정보 맵 조회 - 성공")
    void getPostInfos_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findAllById(List.of(100L))).willReturn(List.of(post));

        Map<Long, PostInfoDto> result = postService.getPostInfos(List.of(100L));

        assertThat(result).containsKey(100L);
    }

    @Test
    @DisplayName("게시글 매칭 정보 맵 조회 - 성공")
    void getPostMatchInfos_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findAllByIdIncludingDeleted(List.of(100L))).willReturn(List.of(post));

        Map<Long, PostMatchInfoDto> result = postService.getPostMatchInfos(List.of(100L));

        assertThat(result).containsKey(100L);
    }

    @Test
    @DisplayName("게시글 강제 삭제 - 성공")
    void forceDeletePost_Success() {
        Post post = createPost(100L, 1L, 1000);

        int result = postService.forceDeletePost(post, "reason");

        assertThat(result).isEqualTo(1000);
        assertThat(post.isDeleted()).isTrue();
        verify(notificationPublisher).sendPostDeleted(1L, 100L);
    }

    @Test
    @DisplayName("삭제 게시글 사유 조회 - 성공")
    void getDeletedPostReason_Success() {
        Post post = createPost(100L, 1L, 1000);
        post.deleteAndReason("reason");
        given(postRepository.findByIdIncludingDeleted(100L)).willReturn(Optional.of(post));

        assertThat(postService.getDeletedPostReason(100L, 1L).postId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("게시글 복구 - 성공")
    void restorePost_Success() {
        Post post = createPost(100L, 1L, 1000);
        post.deleteAndReason("reason");

        int result = postService.restorePost(post);

        assertThat(result).isEqualTo(1000);
        assertThat(post.isDeleted()).isFalse();
        verify(notificationPublisher).sendPostRestored(1L, 100L);
    }

    @Test
    @DisplayName("삭제 포함 게시글 조회 - 성공")
    void getPostByIdIncludingDeleted_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByIdIncludingDeleted(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostByIdIncludingDeleted(100L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("관리자 게시글 목록 조회 - 성공")
    void getPostsForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of(createPost(100L, 1L, 1000)));
        given(postRepository.findAllForAdminByAuthorIds(List.of(1L), PostStatus.OPEN, "keyword", pageable)).willReturn(page);

        Page<Post> result = postService.getPostsForAdmin(List.of(1L), PostStatus.OPEN, "keyword", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 매칭 후보 게시글 조회 - 성공")
    void findAiMatchingCandidatePosts_Success() {
        Sort sort = Sort.by("meetAt").ascending();
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByAuthorIdInAndStatusAndMeetAtAfter(eq(List.of(1L)), eq(PostStatus.OPEN), any(LocalDateTime.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(post)));

        List<Post> result = postService.findAiMatchingCandidatePosts(List.of(1L), sort);

        assertThat(result).containsExactly(post);
    }

    @Test
    @DisplayName("NOWAIT 비관적 잠금 게시글 조회 - 성공")
    void getPostWithPessimisticLockNowait_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByIdWithPessimisticLockNowait(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostWithPessimisticLockNowait(100L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("비관적 잠금 게시글 조회 - 성공")
    void getPostWithPessimisticLock_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findByIdWithPessimisticLock(100L)).willReturn(Optional.of(post));

        Post result = postService.getPostWithPessimisticLock(100L);

        assertThat(result).isSameAs(post);
    }

    @Test
    @DisplayName("게시글 상태 변경 - 성공")
    void changePostStatus_Success() {
        Post post = createPost(100L, 1L, 1000);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.changePostStatus(100L, PostStatus.MATCHED);

        assertThat(post.getStatus()).isEqualTo(PostStatus.MATCHED);
    }

    @Test
    @DisplayName("게시글 조회 - 없음")
    void getPostById_NotFound_ThrowsException() {
        given(postRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(100L))
                .isInstanceOf(RuntimeException.class);
    }

    private Post createPost(Long id, Long authorId, int deposit) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("place")
                .placeLat(new BigDecimal("37.0"))
                .placeLng(new BigDecimal("127.0"))
                .content("content")
                .authorDeposit(deposit)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private UserInfoDto userInfo(Long userId, Long universityId) {
        return new UserInfoDto(userId, "nickname" + userId, "major", "24", new BigDecimal("36.5"), universityId);
    }
}
