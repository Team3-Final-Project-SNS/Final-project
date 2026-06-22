package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostInternalService 단위 테스트")
class PostInternalServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostInternalServiceImpl postInternalService;

    @Test
    @DisplayName("게시글 ID로 내부 조회하면 게시글 정보를 반환한다")
    void getPostInfo_shouldReturnPostInfo() {
        Post post = post();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        PostInfoDto response = postInternalService.getPostInfo(10L);

        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.authorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("게시글이 없으면 내부 조회에 실패한다")
    void getPostById_shouldThrowWhenPostNotFound() {
        when(postRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postInternalService.getPostById(10L))
                .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("게시글 ID 목록이 비어 있으면 저장소 조회 없이 빈 맵을 반환한다")
    void getPostInfos_shouldReturnEmptyMapWhenIdsEmpty() {
        Map<Long, PostInfoDto> response = postInternalService.getPostInfos(List.of());

        assertThat(response).isEmpty();
        verifyNoInteractions(postRepository);
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
