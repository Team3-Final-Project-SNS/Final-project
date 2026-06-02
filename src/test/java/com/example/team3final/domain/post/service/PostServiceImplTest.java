package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.DeletePostResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.repository.PostRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    @DisplayName("게시글 생성 - 성공")
    void createPost_Success() {
        // given
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusHours(1))
                .placeName("정문")
                .authorDeposit(500)
                .maxApplicants(1)
                .build();
        UserInfoDto userInfo = mock(UserInfoDto.class);
        given(userInfo.nickname()).willReturn("Nick");
        given(userService.getUserInfo(1L)).willReturn(userInfo);
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CreatePostResponseDto result = postService.createPost(1L, request);

        // then
        assertNotNull(result);
        assertEquals("Nick", result.authorNickname());
        verify(userPointService).deductPoint(1L, 500, null);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("게시글 생성 - 실패 (과거 시간)")
    void createPost_Fail_PastTime() {
        // given
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().minusHours(1))
                .build();

        // when & then
        PostException exception = assertThrows(PostException.class, () -> postService.createPost(1L, request));
        assertEquals(ErrorCode.POST_INVALID_MEET_AT, exception.getErrorCode());
    }

    @Test
    @DisplayName("게시글 수정 - 성공")
    void updatePost_Success() {
        // given
        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusHours(2), "Place", null, null, "Content", 600
        );
        Post post = mock(Post.class);
        given(post.isAuthor(1L)).willReturn(true);
        given(post.isOpen()).willReturn(true);
        given(post.getAuthorDeposit()).willReturn(500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when
        UpdatePostResponseDto result = postService.updatePost(100L, 1L, request);

        // then
        assertNotNull(result);
        verify(userPointService).deductEditDeposit(1L, 100);
        verify(post).update(any(), any(), any(), any(), any(), eq(600));
    }

    @Test
    @DisplayName("게시글 수정 - 예치금 감소 시 차액 환급")
    void updatePost_Success_RefundDepositDifference() {
        // given
        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusHours(2), "Place", null, null, "Content", 400
        );
        Post post = mock(Post.class);
        given(post.isAuthor(1L)).willReturn(true);
        given(post.isOpen()).willReturn(true);
        given(post.getAuthorDeposit()).willReturn(500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when
        UpdatePostResponseDto result = postService.updatePost(100L, 1L, request);

        // then
        assertNotNull(result);
        verify(userPointService).refundEditDeposit(1L, 100);
        verify(post).update(any(), any(), any(), any(), any(), eq(400));
    }

    @Test
    @DisplayName("게시글 삭제 - 성공")
    void deletePost_Success() {
        // given
        Post post = mock(Post.class);
        given(post.isAuthor(1L)).willReturn(true);
        given(post.isOpen()).willReturn(true);
        given(post.getAuthorDeposit()).willReturn(500);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        // when
        DeletePostResponseDto result = postService.deletePost(100L, 1L);

        // then
        assertNotNull(result);
        assertEquals(500, result.refundedPoint());
        verify(userPointService).refundPoint(1L, 500, null);
        verify(post).delete();
    }
}
