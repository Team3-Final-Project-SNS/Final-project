package com.example.team3final.domain.post.service;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLifecycleService 단위 테스트")
class PostLifecycleServiceTest {

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PostLifecycleServiceImpl postLifecycleService;

    @Test
    @DisplayName("게시글 상태 변경을 요청하면 엔티티 상태를 변경한다")
    void changePostStatus_shouldChangeStatus() {
        Post post = post();
        when(postInternalService.getPostById(10L)).thenReturn(post);

        postLifecycleService.changePostStatus(10L, PostStatus.CANCELLED);

        assertThat(post.getStatus()).isEqualTo(PostStatus.CANCELLED);
    }

    @Test
    @DisplayName("매칭된 게시글 완료를 요청하면 게시글을 완료 상태로 변경한다")
    void completePost_shouldCompleteMatchedPost() {
        Post post = post();
        post.match();
        when(postInternalService.getPostById(10L)).thenReturn(post);

        postLifecycleService.completePost(10L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    private Post post() {
        Post post = Post.builder()
                .authorId(1L)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(300)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", 10L);
        return post;
    }
}
