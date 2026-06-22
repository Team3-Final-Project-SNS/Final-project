package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("매칭 취소 동시성 통합 테스트")
class CancelMatchConcurrencyTest {

    @Autowired
    private MatchCommandService matchCommandService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @MockitoBean
    private ChatInternalService chatInternalService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ZSetOperations<String, String> zSetOperations;

    private Long hostId;
    private Long guestId;
    private Long postId;
    private Long matchId;
    private String emailSuffix;

    private static final int HOST_DEPOSIT = 1000;
    private static final int GUEST_DEPOSIT = 500;

    @BeforeEach
    void setUp() {
        emailSuffix = "-" + System.nanoTime();
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            User host = User.builder()
                    .email("cancel-test-host" + emailSuffix + "@test.com")
                    .password("encodedPassword123!")
                    .name("취소테스트호스트")
                    .nickname("cancel-host")
                    .universityId(1L)
                    .major("컴퓨터공학과")
                    .studentNumber("20240001")
                    .birthDate(LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .build();
            User savedHost = userRepository.save(host);
            savedHost.addFreePoint(5000);
            userRepository.save(savedHost);
            hostId = savedHost.getId();

            User guest = User.builder()
                    .email("cancel-test-guest" + emailSuffix + "@test.com")
                    .password("encodedPassword123!")
                    .name("취소테스트게스트")
                    .nickname("cancel-guest")
                    .universityId(1L)
                    .major("경영학과")
                    .studentNumber("20240002")
                    .birthDate(LocalDate.of(2000, 2, 1))
                    .gender(Gender.FEMALE)
                    .build();
            User savedGuest = userRepository.save(guest);
            savedGuest.addFreePoint(5000);
            userRepository.save(savedGuest);
            guestId = savedGuest.getId();

            Post post = Post.builder()
                    .authorId(hostId)
                    .meetAt(LocalDateTime.now().plusDays(1))
                    .placeName("정문")
                    .placeLat(new BigDecimal("37.5665000"))
                    .placeLng(new BigDecimal("126.9780000"))
                    .content("취소 동시성 테스트용 게시글")
                    .authorDeposit(HOST_DEPOSIT)
                    .maxApplicants(2)
                    .build();
            Post savedPost = postRepository.save(post);
            savedPost.changeStatus(PostStatus.MATCHED);
            postRepository.save(savedPost);
            postId = savedPost.getId();

            Match savedMatch = matchRepository.save(Match.builder()
                    .postId(postId)
                    .applicantId(guestId)
                    .applicantDeposit(GUEST_DEPOSIT)
                    .build());
            matchId = savedMatch.getId();

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionStatus txStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            if (matchId != null) {
                pointTransactionRepository.deleteByMatchId(matchId);
                entityManager.createNativeQuery("DELETE FROM matches WHERE match_id = :matchId")
                        .setParameter("matchId", matchId)
                        .executeUpdate();
            }
            if (postId != null) {
                entityManager.createNativeQuery("DELETE FROM posts WHERE post_id = :postId")
                        .setParameter("postId", postId)
                        .executeUpdate();
            }
            if (hostId != null) {
                userRepository.deleteById(hostId);
            }
            if (guestId != null) {
                userRepository.deleteById(guestId);
            }
            transactionManager.commit(txStatus);
        } catch (Exception e) {
            transactionManager.rollback(txStatus);
        }
    }

    @Test
    @Order(1)
    @DisplayName("HOST와 GUEST가 동시에 같은 매칭을 취소하면 1건만 성공해야 한다")
    void concurrentCancel_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                matchCommandService.cancelMatch(matchId, hostId, new CancelMatchRequestDto("급한 일 생김"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                matchCommandService.cancelMatch(matchId, guestId, new CancelMatchRequestDto("사정 생김"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get())
                .as("취소 성공은 정확히 1건이어야 한다")
                .isEqualTo(1);
        assertThat(failCount.get())
                .as("취소 실패도 정확히 1건이어야 한다")
                .isEqualTo(1);

        long refundTxCount = pointTransactionRepository.countByMatchIdAndTransactionTypeIn(
                matchId,
                List.of(PointTransactionType.REFUND, PointTransactionType.PARTIAL_REFUND)
        );
        assertThat(refundTxCount)
                .as("환불 PointTransaction은 최대 2건이어야 한다")
                .isLessThanOrEqualTo(2);
    }
}
