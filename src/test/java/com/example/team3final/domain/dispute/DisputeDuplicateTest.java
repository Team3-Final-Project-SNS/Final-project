package com.example.team3final.domain.dispute;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("이의제기 중복 제출 방어 검증 테스트")
class DisputeDuplicateTest {

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 테스트용 고정값 - 실제 DB에 없는 ID 사용
    private static final Long TEST_MATCH_ID = 9999L;
    private static final Long TEST_SUBMITTER_ID = 8888L;

    @AfterEach
    void tearDown() {
        // 테스트 데이터 수동 정리 (트랜잭션 수동 처리)
        TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            disputeRepository.deleteAll(
                    disputeRepository.findAll().stream()
                            .filter(d -> d.getMatchId().equals(TEST_MATCH_ID))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    // ====================================================================
    // 테스트 1: 정상 케이스 — 첫 번째 이의제기는 저장 성공
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("첫 번째 이의제기 제출 -> 정상 저장되어야 한다")
    void firstDispute_shouldSaveSuccessfully() {
        // given
        Dispute dispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);

        // when & then
        // 예외 없이 저장되어야 함
        assertThatCode(() -> disputeRepository.saveAndFlush(dispute))
                .doesNotThrowAnyException();

        // DB에 1건 저장됐는지 확인
        assertThat(disputeRepository.existsByMatchIdAndSubmitterId(TEST_MATCH_ID, TEST_SUBMITTER_ID))
                .isTrue();
    }

    // ====================================================================
    // 테스트 2: 핵심 케이스 — 같은 matchId + submitterId로 2번 제출 시 차단
    // ====================================================================
    //
    // 기대 동작:
    //   첫 번째 저장 → 성공
    //   두 번째 저장 → DataIntegrityViolationException 발생
    //   (uk_dispute_match_submitter UNIQUE 제약 위반)
    //
    // 이 테스트가 통과 = DB 레벨에서 중복 이의제기가 원천 차단됨
    // ====================================================================

    @Test
    @Order(2)
    @DisplayName("동일 matchId + submitterId로 2번 제출 -> UNIQUE 제약으로 차단 되어야 한다")
    void duplicateDispute_shouldBeBlockedByUniqueConstraint() {
        // given - 첫 번째 이의제기 저장
        Dispute firstDispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);
        disputeRepository.saveAndFlush(firstDispute); // 첫 번째는 성공

        // given - 두 번째 이의제기 (같은 matchId + submitterId)
        Dispute duplicateDispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);

        // when & then
        // UNIQUE 제약 위반 -> DataIntegrityViolationException 발생해야 함
        assertThatThrownBy(() -> disputeRepository.saveAndFlush(duplicateDispute))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_dispute_match_submitter");
    }

    // ====================================================================
    // 테스트 3: 다른 사용자는 같은 매칭에 이의제기 가능
    // ====================================================================
    //
    // 기대 동작:
    //   matchId=9999, submitterId=8888 → 성공
    //   matchId=9999, submitterId=7777 → 성공 (다른 사용자)
    //   → UNIQUE 제약은 (match_id, submitter_id) 조합이므로
    //     같은 매칭이라도 다른 사용자는 독립적으로 이의제기 가능
    //
    // 실제 시나리오: 등록자와 신청자가 각자 이의제기를 낼 수 있음
    // ====================================================================

    @Test
    @Order(3)
    @DisplayName("같은 matchId라도 다른 submitterId는 이의제기 가능해야 한다.")
    void differentSubmitter_sameMatch_shouldSaveSuccessfully() {
        // given
        Long anotherSubmitterId = 7777L;

        Dispute firstDispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);
        Dispute anotherDispute = buildDispute(TEST_MATCH_ID, anotherSubmitterId, null);

        // when & then - 둘 다 성공해야 함
        assertThatCode(() -> {
            disputeRepository.saveAndFlush(firstDispute);
            disputeRepository.saveAndFlush(anotherDispute);
        }).doesNotThrowAnyException();

        // DB에 2건 저장됐는지 확인
        assertThat(disputeRepository.existsByMatchIdAndSubmitterId(
                TEST_MATCH_ID, TEST_SUBMITTER_ID)).isTrue();
        assertThat(disputeRepository.existsByMatchIdAndSubmitterId(
                TEST_MATCH_ID, anotherSubmitterId)).isTrue();
    }

    // ====================================================================
    // 테스트 4: 재이의제기는 UNIQUE 제약에 걸리지 않아야 함
    // ====================================================================
    //
    // 재이의제기 구조:
    //   원본 이의제기: matchId=9999, submitterId=8888, parentDisputeId=null
    //   재이의제기:   matchId=9999, submitterId=8888, parentDisputeId=원본ID
    //   → 같은 matchId + submitterId지만 parentDisputeId가 다름
    //   → 별도 레코드이므로 UNIQUE 제약 위반이 아님
    //
    // UNIQUE 제약이 (match_id, submitter_id)에만 걸려있으므로
    // parentDisputeId 값과 무관하게 같은 조합이면 차단됨
    //
    // ⚠️ 따라서 재이의제기는 서비스 레이어에서 별도 검증이 필요:
    //    DisputeService에서 parentDisputeId 기반으로 중복 확인
    //    → existsByMatchIdAndSubmitterIdAndParentDisputeId() 사용
    // ====================================================================

    @Test
    @Order(4)
    @DisplayName("재이의제기(parentDisputeId 존제)는 UNIQUE 제약에 걸린다 -> 서비스 레이어 검증 필요")
    void reDispute_shouldBeBlockedByUniqueConstraint_requiredServiceLayerValidation() {
        // given - 원본 이의제기 저장
        Dispute originalDispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);
        Dispute savedOriginal = disputeRepository.saveAndFlush(originalDispute);

        // given - 재이의제기 (parentDisputeId = 원본 ID)
        Dispute reDispute = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, savedOriginal.getId());

        // when & then
        // UNIQUE 제약은 (match_id, submitter_id) 조합만 보므로
        // parentDisputeId 값과 무관하게 같은 조합이면 DB 레벨에서 차단됨
        // → 재이의제기의 중복 방지는 서비스 레이어에서 처리해야 함
        assertThatThrownBy(() -> disputeRepository.saveAndFlush(reDispute))
                .isInstanceOf(DataIntegrityViolationException.class);

        System.out.println("[주의] 재이의제기는 UNIQUE 제약이 아닌 서비스 레이어에서 검증 필요");
        System.out.println("       DisputeService.existsByMatchIdAndSubmitterIdAndParentDisputeId() 사용");
    }

    // ====================================================================
    // 테스트 5: 동시 제출 시나리오 — 빠른 두 번 클릭 재현
    // ====================================================================
    //
    // 실제 발생 상황: 사용자가 이의제기 버튼을 빠르게 2번 클릭
    // → 두 요청이 거의 동시에 서버에 도달
    // → 둘 다 "이미 제출한 이의제기 없음" 확인 후 INSERT 시도
    // → UNIQUE 제약으로 하나는 차단
    //
    // 이 테스트는 비관적으로 단일 스레드에서 재현하지만
    // DB UNIQUE 제약은 멀티스레드에서도 동일하게 동작
    // ====================================================================

    @Test
    @Order(5)
    @DisplayName("버튼 두 번 클릭 시나리오 -> 하나만 저장되어야 한다")
    void doubleClick_onlyOneDisputeShouldBeSaved() {
        // given - 두 번 클릭으로 생성된 동일한 이의제기 요청
        Dispute firstClick = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);
        Dispute secondClick = buildDispute(TEST_MATCH_ID, TEST_SUBMITTER_ID, null);

        // when
        disputeRepository.saveAndFlush(firstClick); // 첫 번째 클릭 → 성공

        // then
        assertThatThrownBy(() -> disputeRepository.saveAndFlush(secondClick))
                .isInstanceOf(DataIntegrityViolationException.class);

        // DB에 정확히 1건만 저장됐는지 확인
        long count = disputeRepository.findAll().stream()
                .filter(d -> d.getMatchId().equals(TEST_MATCH_ID)
                        && d.getSubmitterId().equals(TEST_SUBMITTER_ID))
                .count();

        assertThat(count)
                .as("이의제기는 정확히 1건만 저장되어야 한다")
                .isEqualTo(1L);
    }

    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    /**
     * 테스트용 Dispute 빌더
     *
     * @param matchId         대상 매칭 ID
     * @param submitterId     제출자 ID
     * @param parentDisputeId 재이의제기인 경우 원본 이의제기 ID (최초 제출 시 null)
     */
    private Dispute buildDispute(Long matchId, Long submitterId, Long parentDisputeId) {
        return Dispute.builder()
                .matchId(matchId)
                .submitterId(submitterId)
                .disputeType(DisputeType.GPS_ERROR)  // 테스트용 임의 타입
                .reason("동시성 테스트용 이의제기 사유입니다.")
                .evidenceUrl(null)                    // 증빙자료 없음
                .parentDisputeId(parentDisputeId)     // 재이의제기 여부
                .build();
    }
}
