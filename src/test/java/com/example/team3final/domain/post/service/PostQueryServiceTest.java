package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostQueryService 단위 테스트")
class PostQueryServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private ReviewAvoidanceService reviewAvoidanceService;

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @InjectMocks
    private PostQueryServiceImpl postQueryService;

    @Test
    @DisplayName("게시글 목록 캐시가 있으면 저장소 조회 없이 캐시 응답을 반환한다")
    void getPosts_shouldReturnCachedResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        PageResponseDto<GetPostsItemResponseDto> cached = new PageResponseDto<>(List.of(), 0, 10, 0, 0, false);
        when(redisPostService.getPostList(1L, PostStatus.OPEN, 0, 10, pageable.getSort().toString()))
                .thenReturn(Optional.of(cached));

        PageResponseDto<GetPostsItemResponseDto> response = postQueryService.getPosts(1L, PostStatus.OPEN, pageable);

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("게시글 목록 조회 페이지 크기가 50을 초과하면 실패한다")
    void getPosts_shouldThrowWhenPageSizeTooLarge() {
        assertThatThrownBy(() -> postQueryService.getPosts(1L, null, PageRequest.of(0, 51)))
                .isInstanceOf(PostException.class);
    }
}
