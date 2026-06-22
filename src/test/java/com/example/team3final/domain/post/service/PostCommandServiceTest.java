package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostCommandService 단위 테스트")
class PostCommandServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserPointService userPointService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private RedisPostService redisPostService;

    @InjectMocks
    private PostCommandServiceImpl postCommandService;

    @Test
    @DisplayName("게시글 생성 시 작성자 책임비를 차감하고 게시글을 저장한다")
    void createPost_shouldDeductPointAndSavePost() {
        CreatePostRequestDto requestDto = createPostRequest(300);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", 10L);
            return post;
        });
        when(userInternalService.findUserById(1L)).thenReturn(user());
        when(userInternalService.getUserInfo(1L)).thenReturn(userInfo(1L));

        postCommandService.createPost(1L, requestDto);

        verify(userPointService).deductPoint(1L, 300, null);
        verify(postRepository).save(any(Post.class));
        verify(redisPostService).evictPostLists();
    }

    @Test
    @DisplayName("게시글 생성 시 책임비가 100 포인트 단위가 아니면 실패한다")
    void createPost_shouldThrowWhenDepositUnitInvalid() {
        CreatePostRequestDto requestDto = createPostRequest(250);

        assertThatThrownBy(() -> postCommandService.createPost(1L, requestDto))
                .isInstanceOf(PostException.class);
    }

    private CreatePostRequestDto createPostRequest(int deposit) {
        return CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(deposit)
                .maxApplicants(2)
                .build();
    }

    private UserInfoDto userInfo(Long userId) {
        return new UserInfoDto(userId, "닉네임", "컴퓨터공학", "20", BigDecimal.valueOf(36.5), 1L);
    }

    private User user() {
        User user = User.builder()
                .email("user@test.com")
                .password("encoded")
                .name("사용자")
                .nickname("닉네임")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(com.example.team3final.domain.user.enums.Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
