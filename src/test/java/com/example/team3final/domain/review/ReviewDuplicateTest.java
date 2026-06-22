package com.example.team3final.domain.review;

import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("후기 중복 제출 방지 통합 테스트")
class ReviewDuplicateTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Long TEST_MATCH_ID = 7001L;
    private static final Long TEST_WRITER_ID = 7002L;

    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            reviewRepository.deleteAll(
                    reviewRepository.findAll().stream()
                            .filter(review -> review.getMatchId().equals(TEST_MATCH_ID)
                                    || review.getMatchId().equals(TEST_MATCH_ID + 1))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    @Test
    @Order(1)
    @DisplayName("첫 번째 후기 작성은 정상 저장되어야 한다")
    void firstReview_shouldSaveSuccessfully() {
        Review review = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        assertThatCode(() -> reviewRepository.saveAndFlush(review))
                .doesNotThrowAnyException();

        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID))
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("같은 matchId와 writerId로 후기를 중복 저장하면 UNIQUE 제약으로 차단되어야 한다")
    void duplicateReview_shouldBeBlockedByUniqueConstraint() {
        reviewRepository.saveAndFlush(buildReview(TEST_MATCH_ID, TEST_WRITER_ID));

        Review duplicate = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(3)
    @DisplayName("같은 matchId라도 writerId가 다르면 후기 작성이 가능해야 한다")
    void differentWriters_shouldBothSaveSuccessfully() {
        Review reviewA = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review reviewB = buildReview(TEST_MATCH_ID, TEST_WRITER_ID + 1);

        assertThatCode(() -> reviewRepository.saveAndFlush(reviewA))
                .doesNotThrowAnyException();
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewB))
                .doesNotThrowAnyException();

        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID)).isTrue();
        assertThat(reviewRepository.existsByMatchIdAndWriterId(TEST_MATCH_ID, TEST_WRITER_ID + 1)).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("같은 writerId라도 matchId가 다르면 후기 작성이 가능해야 한다")
    void sameWriter_differentMatch_shouldSaveSuccessfully() {
        Review reviewMatch1 = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review reviewMatch2 = buildReview(TEST_MATCH_ID + 1, TEST_WRITER_ID);

        assertThatCode(() -> reviewRepository.saveAndFlush(reviewMatch1))
                .doesNotThrowAnyException();
        assertThatCode(() -> reviewRepository.saveAndFlush(reviewMatch2))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(5)
    @DisplayName("후기 제출을 빠르게 두 번 요청해도 DB에는 1건만 저장되어야 한다")
    void doubleClick_onlyOneReviewShouldBeSaved() {
        Review firstClick = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);
        Review secondClick = buildReview(TEST_MATCH_ID, TEST_WRITER_ID);

        reviewRepository.saveAndFlush(firstClick);

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(secondClick))
                .isInstanceOf(DataIntegrityViolationException.class);

        long count = reviewRepository.findAll().stream()
                .filter(review -> review.getMatchId().equals(TEST_MATCH_ID)
                        && review.getWriterId().equals(TEST_WRITER_ID))
                .count();

        assertThat(count)
                .as("후기는 정확히 1건만 저장되어야 한다")
                .isEqualTo(1L);
    }

    private Review buildReview(Long matchId, Long writerId) {
        return Review.builder()
                .matchId(matchId)
                .writerId(writerId)
                .tagScoreDelta(1)
                .build();
    }
}
