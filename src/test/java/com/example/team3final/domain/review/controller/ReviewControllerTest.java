package com.example.team3final.domain.review.controller;

import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.service.ReviewService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("후기 컨트롤러 통합 테스트")
class ReviewControllerTest extends ControllerTestSupport {

    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new ReviewController(reviewService));
    }

    @Test
    @DisplayName("후기 작성 API는 매칭 ID, 작성자 ID, 후기 요청을 서비스로 전달하고 201을 반환한다")
    void createReview_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/matches/10/reviews")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goodTags": ["ON_TIME"],
                                  "badTags": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(reviewService).createReview(eq(10L), eq(1L), any(CreateReviewRequestDto.class));
    }

    @Test
    @DisplayName("내가 작성한 후기 목록 조회 API는 인증 사용자 ID를 서비스로 전달한다")
    void getReviews_shouldDelegateAuthenticatedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/me/reviews")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reviewService).getWrittenReviews(1L);
    }
}
