package com.example.team3final.domain.review;

import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("후기 중복 제출 방지 및 매너온도 동시성 테스트")
class ReviewDuplicateTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Long TEST_MATCH_ID  = 7001L;
    private static final Long TEST_WRITER_ID = 7002L;

    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            reviewRepository.deleteAll(
                    reviewRepository.findAll().stream()
                            .filter(r -> r.getMatchId().equals(TEST_MATCH_ID))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    // ====================================================================
    // 테스트 1: 정상 케이스 — 첫 번째 후기 저장 성공
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("첫 번째 후기 작성 → 정상 저장되어야 한다")
    void firstReview_shouldSaveSuccessfully() {
        // given
        Review review = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        // when & then
        assertThatCode(() -> reviewRepository.saveAndFlush(review))
                .doesNotThrowAnyException();

        // 저장 확인
        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID))
                .isTrue();
    }

    // ====================================================================
    // 테스트 2: 핵심 케이스 — 같은 matchId + writerId 중복 저장 차단
    // ====================================================================
    // uk_reviews_match_writer UNIQUE 제약 위반 확인
    // 제약 이름이 예외 메시지에 포함되는지도 검증
    // ====================================================================

    @Test
    @Order(2)
    @DisplayName("같은 matchId + writerId 로 후기를 2번 저장하면 UNIQUE 제약으로 차단되어야 한다")
    void duplicateReview_shouldBeBlockedByUniqueConstraint() {
        // given — 첫 번째 후기 저장
        reviewRepository.saveAndFlush(buildReview(TEST_MATCH_ID, TEST_WRITER_ID));

        // given — 두 번째 후기 (같은 matchId + writerId)
        Review duplicate = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        // when & then — UNIQUE 제약 위반 → DataIntegrityViolationException
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_reviews_match_writer");  // 제약 이름 확인
    }

    // ====================================================================
    // 테스트 3: 다른 신청자는 같은 매칭에 후기 작성 가능
    // ====================================================================
    // 그룹 매칭 시나리오:
    //   신청자 A (TEST_WRITER_ID)  → matchId 7001 후기 성공
    //   신청자 B (TEST_WRITER_ID+1) → matchId 7001 후기 성공
    //   (match_id가 같아도 writer_id가 다르면 복합 UNIQUE 통과)
    // ====================================================================

    @Test
    @Order(3)
    @DisplayName("같은 matchId 라도 다른 writerId 는 후기 작성이 가능해야 한다 (그룹 매칭)")
    void differentWriters_shouldBothSaveSuccessfully() {
        // given
        Review reviewA = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review reviewB = buildReview(TEST_MATCH_ID, TEST_WRITER_ID + 1);  // 다른 신청자

        // when & then — 둘 다 저장 성공
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewA))
                .doesNotThrowAnyException();
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewB))
                .doesNotThrowAnyException();

        // 두 건 모두 저장됐는지 확인
        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID)).isTrue();
        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID + 1)).isTrue();
    }

    // ====================================================================
    // 테스트 4: 같은 사람이 다른 매칭에는 후기 작성 가능
    // ====================================================================
    // 복합 UNIQUE (match_id, writer_id) 이므로
    // 같은 writer_id 라도 match_id가 다르면 통과해야 함
    // ====================================================================

    @Test
    @Order(4)
    @DisplayName("같은 writerId 라도 다른 matchId 에는 후기 작성이 가능해야 한다")
    void sameWriter_differentMatch_shouldSaveSuccessfully() {
        // given
        Review reviewMatch1 = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review reviewMatch2 = buildReview(TEST_MATCH_ID + 1, TEST_WRITER_ID);  // 다른 매칭

        // when & then
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewMatch1))
                .doesNotThrowAnyException();
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewMatch2))
                .doesNotThrowAnyException();
    }

    // ====================================================================
    // 테스트 5: 빠른 두 번 클릭 시나리오 — 멱등성 검증
    // ====================================================================
    // 사용자가 후기 제출 버튼을 빠르게 2번 클릭하는 상황 재현
    // 하나만 저장되고 두 번째는 UNIQUE 제약으로 차단되어야 함
    // ====================================================================

    @Test
    @Order(5)
    @DisplayName("후기 버튼 두 번 클릭 시나리오 → DB에 정확히 1건만 저장되어야 한다")
    void doubleClick_onlyOneReviewShouldBeSaved() {
        // given — 빠른 두 번 클릭으로 생성된 동일한 후기 요청
        Review firstClick  = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review secondClick = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        // when
        reviewRepository.saveAndFlush(firstClick);   // 첫 번째 클릭 → 성공

        // then — 두 번째 클릭은 UNIQUE 제약으로 차단
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(secondClick))
                .isInstanceOf(DataIntegrityViolationException.class);

        // DB에 정확히 1건만 저장됐는지 확인
        long count = reviewRepository.findAll().stream()
                .filter(r -> r.getMatchId().equals(TEST_MATCH_ID)
                        && r.getWriterId().equals(TEST_WRITER_ID))
                .count();

        assertThat(count)
                .as("후기는 정확히 1건만 저장되어야 한다")
                .isEqualTo(1L);
    }

    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    private Review buildReview(Long matchId, Long writerId) {
        return Review.builder()
                .matchId(matchId)
                .writerId(writerId)
                .tagScoreDelta(1)   // 좋아요 태그 기준 +1점
                .build();
    }
}
