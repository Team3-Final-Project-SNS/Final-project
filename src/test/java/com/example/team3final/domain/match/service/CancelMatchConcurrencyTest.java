package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatService;
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
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CancelMatchConcurrencyTest {

    @Autowired
    private MatchService matchService;

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
    private NotificationPublisher notificationPublisher; // Kafka 알림 발행자

    @MockitoBean
    private ChatService chatService; // 채팅방 퇴장/비활성화 처리

    // ── save() 후 DB가 발급한 id를 담을 인스턴스 변수 ──
    // 고정 상수 제거: @GeneratedValue id를 빌더로 직접 세팅 불가
    // → setUp()에서 저장 후 반환값으로 id를 받아 테스트 메서드에서 공유
    private Long hostId;
    private Long guestId;
    private Long postId;
    private Long matchId;

    private static final int HOST_DEPOSIT  = 1000;
    private static final int GUEST_DEPOSIT = 500;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {

            // ── HOST 유저 생성 ──
            // 기존 MatchConcurrencyTest 패턴과 동일:
            //   1) 필드 주입 없이 빌더로 엔티티 생성
            //   2) save() → DB가 id 부여
            //   3) 포인트는 도메인 메서드(addFreePoint)로 별도 세팅
            User host = User.builder()
                    .email("cancel-test-host@test.com") // 다른 테스트와 이메일 충돌 방지
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
            savedHost.addFreePoint(5000); // 포인트 도메인 메서드로 세팅
            userRepository.save(savedHost);
            hostId = savedHost.getId(); // ← DB 발급 id 저장

            // ── GUEST 유저 생성 ──
            User guest = User.builder()
                    .email("cancel-test-guest@test.com")
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
            guestId = savedGuest.getId(); // ← DB 발급 id 저장

            // ── Post 생성 ──
            // status, currentApplicants는 빌더 내부에서 고정값으로 초기화됨
            // (기존 MatchConcurrencyTest createTestPost() 주석 참고)
            // → 이 테스트에서는 MATCHED 상태가 필요하므로 저장 후 도메인 메서드로 전환
            Post post = Post.builder()
                    .authorId(hostId)
                    .meetAt(LocalDateTime.now().plusDays(1)) // 내일 — 취소 가능 조건
                    .placeName("정문")
                    .placeLat(new BigDecimal("37.5665000"))
                    .placeLng(new BigDecimal("126.9780000"))
                    .content("취소 동시성 테스트용 게시글")
                    .authorDeposit(HOST_DEPOSIT)
                    .maxApplicants(2)
                    .build();
            Post savedPost = postRepository.save(post);
            // 빌더 생성 시 status=OPEN으로 고정됨 → MATCHED로 전환 필요
            // (cancelMatch() 내부 검증: post가 MATCHED여야 함)
            savedPost.changeStatus(PostStatus.MATCHED);
            postRepository.save(savedPost);
            postId = savedPost.getId(); // ← DB 발급 id 저장

            // ── Match 생성 ──
            // status는 빌더 내부에서 PENDING 또는 MATCHED로 고정됨
            // Match 엔티티 빌더 파라미터는 실제 Match.java 기준으로 작성
            Match match = Match.builder()
                    .postId(postId)
                    .applicantId(guestId)
                    .applicantDeposit(GUEST_DEPOSIT)
                    .build();
            Match savedMatch = matchRepository.save(match);
            // 빌더 생성 후 MATCHED 상태가 아니라면 도메인 메서드로 전환
            // (실제 Match 엔티티에 changeStatus() 또는 confirm() 같은 메서드가 있으면 사용)
            matchId = savedMatch.getId(); // ← DB 발급 id 저장

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // PostStatusConcurrencyTest 패턴과 동일:
        // PlatformTransactionManager로 직접 트랜잭션 관리
        // FK 순서: point_transactions → matches → posts → users
        TransactionStatus txStatus = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            // PointTransaction은 match_id 참조 → 먼저 삭제
            if (matchId != null) {
                pointTransactionRepository.deleteByMatchId(matchId);
            }

            // matches: @SQLDelete(소프트 딜리트)가 있으면 native query로 물리 삭제
            entityManager.createNativeQuery("DELETE FROM matches WHERE match_id = :matchId")
                    .setParameter("matchId", matchId)
                    .executeUpdate();

            // posts: @SQLDelete가 있으면 native query 사용
            entityManager.createNativeQuery("DELETE FROM posts WHERE post_id = :postId")
                    .setParameter("postId", postId)
                    .executeUpdate();

            // users: soft delete 없으면 deleteById 가능
            if (hostId  != null) userRepository.deleteById(hostId);
            if (guestId != null) userRepository.deleteById(guestId);

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
        CountDownLatch startLatch = new CountDownLatch(1); // 동시 출발 신호
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        // ── HOST 취소 스레드 ──
        executor.submit(() -> {
            try {
                startLatch.await();
                CancelMatchRequestDto req = new CancelMatchRequestDto("급한 일 생김");
                matchService.cancelMatch(matchId, hostId, req);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 비관적 락 대기 후 두 번째 트랜잭션이 읽으면:
                //   첫 번째가 이미 CANCELLED로 변경 → 상태 검증(MATCH_INVALID_STATUS)에서 422
                // → 정상적인 실패이므로 failCount만 올림
                System.err.println("[HOST 스레드 실패] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // ── GUEST 취소 스레드 ──
        executor.submit(() -> {
            try {
                startLatch.await();
                CancelMatchRequestDto req = new CancelMatchRequestDto("사정 생김");
                matchService.cancelMatch(matchId, guestId, req);
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("[GUEST 스레드 실패] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                failCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // 동시 출발!
        doneLatch.await();
        executor.shutdown();

        // ── 검증 ──

        // 1. 취소 성공은 정확히 1건
        //    2건이면 중복 처리 버그 (비관적 락 미적용 시 발생)
        assertThat(successCount.get())
                .as("취소 성공은 정확히 1건이어야 한다")
                .isEqualTo(1);

        // 2. 환불 PointTransaction이 최대 2건 (취소자 PARTIAL_REFUND 1건 + 상대방 REFUND 1건)
        //    3건 이상이면 환불이 중복 실행된 것
        long refundTxCount = pointTransactionRepository
                .countByMatchIdAndTransactionTypeIn(
                        matchId,
                        List.of(PointTransactionType.REFUND, PointTransactionType.PARTIAL_REFUND)
                );
        assertThat(refundTxCount)
                .as("환불 PointTransaction은 최대 2건이어야 한다")
                .isLessThanOrEqualTo(2);
    }
}