package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.*;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.review.util.ReviewRedisZSetKeys;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService{

    private final MatchRepository matchRepository;
    private final ChatService chatService;
    private final UserPointService userPointService;
    private final UserService userService;
    private final PostService postService;
    private final NotificationPublisher notificationPublisher;  // 알림 발송용
    private final ReviewAvoidanceService reviewAvoidanceService;
    private final StringRedisTemplate redisTemplate; // ZSet 예약용
    private final ReportService reportService;
    private final UserLocationCleanupService userLocationCleanupService;
    private final RedissonClient redissonClient;

    @Autowired
    @Lazy
    private MatchServiceImpl self;

    @Value("${match.lock.group-wait-ms:500}")
    // @Value: application.yml에서 값 주입, 없으면 기본값 500 사용
    private long groupLockWaitMs;

    private static final String MATCH_LOCK_KEY = "match:lock:post:";
    private static final long REDIS_LOCK_LEASE_SECONDS = 5;

    @Override
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {

        String lockKey = MATCH_LOCK_KEY + postId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        CreateMatchResponseDto result = null; // 알림 발송에 필요한 정보 보관용

        try {
            Post post = postService.getPostById(postId);
            long waitTime = (post.getMaxApplicants() == 2) ? 0L : groupLockWaitMs;
            acquired = lock.tryLock(waitTime, REDIS_LOCK_LEASE_SECONDS * 1000, TimeUnit.MILLISECONDS);

            if (!acquired) {
                throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
            }

            // DB 작업만 트랜잭션 안에서 처리 (알림 발송 제외)
            result = self.createMatchInTransaction(postId, applicantId);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
                // ✅ 락 해제 후 알림 발송
                // → Kafka 대기 시간(3초×2)이 락 점유 시간에서 완전히 제거됨
                // → 다음 스레드가 락을 빠르게 획득 가능 → 단체 매칭 순차 처리 보장
                // result가 null이면 createMatchInTransaction()에서 예외 발생한 것
                // → 매칭 실패 케이스이므로 알림 발송 불필요
                if (result != null) {
                    notificationPublisher.sendMatchApplied(result.authorId(), result.matchId());
                    notificationPublisher.sendMatchConfirmed(result.applicantId(), result.matchId());
                }
            }

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateMatchResponseDto createMatchInTransaction(Long postId, Long applicantId) {

        Post post = postService.getPostWithPessimisticLock(postId);

        // 1. 본인 소유 게시글 신청 차단
        if (post.getAuthorId().equals(applicantId)) {
            throw new MatchException(ErrorCode.MATCH_SELF_APPLY);
        }

        // 다시 만나고 싶지 않아요 관계가 있으면 목록에서 보이지 않아야 하고,
        // postId를 직접 알아도 신청할 수 없어야 하므로 매칭 생성 단계에서 한 번 더 차단합니다.
        if (reviewAvoidanceService.existsAvoidRelation(applicantId, post.getAuthorId())) {
            throw new MatchException(ErrorCode.MATCH_AVOIDED_USER);
        }

        // 2. 게시글 상태 검증
        if (post.getStatus() == PostStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        }
        if (post.getStatus() == PostStatus.CANCELLED) {
            throw new MatchException(ErrorCode.MATCH_POST_CLOSED);
        }
        if (post.getStatus() != PostStatus.OPEN) {
            throw new MatchException(ErrorCode.MATCH_POST_CLOSED);
        }

        // 신고 접수 중인 게시글 차단
        boolean isUnderReport = reportService.existsPendingReport(post.getId());
        if (isUnderReport) {
            throw new MatchException(ErrorCode.MATCH_POST_UNDER_REPORT);
        }

        // 2-1. 중복 신청 차단 (같은 게시글에 같은 사용자 두 번 신청 불가)
        // MATCHED 상태인 경우만 중복으로 판단
        // 기획서: "Post 상태 OPEN (재신청 가능)" → CANCELLED된 매칭은 재신청 허용
        if (matchRepository.existsByPostIdAndApplicantIdAndStatus(
                postId, applicantId, MatchStatus.MATCHED)) {
            throw new MatchException(ErrorCode.MATCH_DUPLICATE_APPLY);
        }

        // 2-2. 활성 매칭 정원 검증 (그룹 매칭 지원)
        if (post.isFull()) {
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        }

        // 3. 신청자 포인트 차감 — 잔액 부족 시 예외 → 트랜잭션 전체 롤백
        // matchId는 아직 생성 전이라 null
        userPointService.deductPoint(applicantId, post.getAuthorDeposit(), null);

        Match match = Match.builder()
                .postId(postId)
                .applicantId(applicantId)
                .applicantDeposit(post.getAuthorDeposit())
                .build();
        Match savedMatch = matchRepository.save(match);

        // 참여 인원 증가
        post.increaseCurrentApplicants();

        // 첫 신청 즉시 채팅방 생성 (정원 관계없이)
        // 채팅방이 없으면 생성, 있으면 기존 채팅방에 멤버만 추가
        Long chatRoomId = null;
        if (!chatService.existsChatRoomByPostId(postId)) {
            // 첫 번째 신청자 → HOST(등록자) + GUEST(신청자) 채팅방 생성
            chatRoomId = chatService.createChatRoom(
                    postId,
                    post.getAuthorId(),
                    applicantId
            );
        } else {
            // 두 번째 이후 신청자 → 기존 채팅방에 GUEST로 추가
            chatService.addChatMember(postId, applicantId);
            chatRoomId = chatService.getChatRoomIdByPostId(postId);
        }

       // 정원이 다 찼을 때만 게시글 상태 MATCHED로 변경
        if (post.isFull()) {
            post.match();
        }

        // 양측 닉네임 조회
        String authorNickname = userService.getUserInfo(post.getAuthorId()).nickname();
        String applicantNickname = userService.getUserInfo(applicantId).nickname();

        // 만남 알림 ZSet 예약 (30분/15분/5분 전)
        // score = 알림 발송할 Unix Timestamp
        // member = matchId (스케줄러가 꺼내서 알림 발송에 사용)
        LocalDateTime meetAt = post.getMeetAt();
        LocalDateTime now = LocalDateTime.now();
        ZoneOffset KST = ZoneOffset.ofHours(9);
        String postIdStr = String.valueOf(postId);
        String matchIdStr = String.valueOf(savedMatch.getId());

        // 30분 전 알림 예약
        if (now.isBefore(meetAt.minusMinutes(30))) {
            double score30 = meetAt.minusMinutes(30).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_30_HOST, postIdStr, score30);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_30_GUEST, matchIdStr, score30);
        }

        // 15분 전 알림 예약
        if (now.isBefore(meetAt.minusMinutes(15))) {
            double score15 = meetAt.minusMinutes(15).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_15_HOST, postIdStr, score15);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_15_GUEST, matchIdStr, score15);
        }

        // 임박 알림 예약 (5분 전)
        if (now.isBefore(meetAt.minusMinutes(5))) {
            double score5 = meetAt.minusMinutes(5).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, postIdStr, score5);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, matchIdStr, score5);
        }

        // 10분 경과 알림 예약
        if (now.isBefore(meetAt.plusMinutes(10))) {
            double scoreOverdue = meetAt.plusMinutes(10).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST, postIdStr, scoreOverdue);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST, matchIdStr, scoreOverdue);
        }

        return CreateMatchResponseDto.of(
                savedMatch,
                post.getAuthorId(),
                post.getAuthorDeposit(),
                authorNickname,
                applicantNickname,
                chatRoomId
        );
    }

    @Override
    public boolean hasAppliedToPost(Long postId, Long applicantId) {
        // 중복 신청 여부는 Match 도메인의 데이터 규칙이므로,
        // 다른 도메인은 Repository 대신 이 서비스 메서드를 통해 확인합니다.
        return matchRepository.existsByPostIdAndApplicantIdAndStatus(postId, applicantId, MatchStatus.MATCHED);
    }

    @Override
    public List<Long> getMatchIdsByPostId(Long postId) {
        // Review 도메인에서 단체 만남 리뷰 평균을 계산할 때 사용합니다.
        // Service-to-Service 규칙에 따라 Review는 MatchRepository를 직접 참조하지 않습니다.
        return matchRepository.findAllByPostId(postId)
                .stream()
                .map(Match::getId)
                .toList();
    }

    @Override
    public List<Match> getCompletedMatchesByPostId(Long postId) {
        // Chat 도메인에서 만남 완료 알림 대상 신청자를 조회할 때 사용합니다.
        return matchRepository.findAllByPostIdAndStatus(postId, MatchStatus.COMPLETED);
    }

    @Override
    public Optional<Match> findCompletedMatchById(Long matchId) {
        // Review 도메인 스케줄러에서 후기 마지막 날 알림 대상 매칭을 조회할 때 사용합니다.
        return matchRepository.findById(matchId)
                .filter(match -> match.getStatus() == MatchStatus.COMPLETED);
    }

    @Override
    @Transactional
    public void markDisputed(Long matchId) {

        Match match = getMatchById(matchId);

        // MATCHED 상태일 때만 DISPUTED로 전환
        // DISPUTED 중인 건에 중복 이의제기가 들어와도 상태가 다시 바뀌지 않도록 방어
        if (match.getStatus() == MatchStatus.MATCHED) {
            match.dispute();
        }
    }

    // 시스템 취소 — QR 만료 시점까지 양측 모두 현장에 있었으나 QR 인증 미완료
    @Override
    @Transactional
    public void cancelMatchBySystem(Long matchId) {

        Match match = getMatchById(matchId);

        // 이미 종결된 건은 스킵 (스케줄러 중복 실행 방어)
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        // Match → CANCELLED
        match.cancel();

        Post post = postService.getPostById(match.getPostId());

        // Post → CANCELLED
        post.cancel();

        // 양측 전액 환불 (둘 다 현장에 있었으므로 귀책 없음 → 패널티 없음)
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);

        // 위치 데이터 삭제 (개인정보 최소 수집 원칙)
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        // 채팅방 비활성화
        chatService.deactivateChatRoom(match.getPostId());
    }

    // 관리자 ACCEPTED 판정
    // 노쇼가 아니라고 인정된 케이스다.
    // 따라서 해당 Match를 정상 완료 처리하고, 포인트도 정상 완료 기준으로 정산한다.
    // 신청자 예치금: 해당 Match 완료 시 전액 반환
    // 등록자 책임비: 같은 Post의 모든 활성 Match가 끝났을 때 1회 반환
    // 반환값 이의제기자에게 실제 반환된 포인트
    @Override
    @Transactional
    public int completeSingleMatchByDispute(Long matchId, Long submitterId) {

        Match match = getMatchById(matchId);

        // 관리자 판정 처리 가능한 상태가 아니면 중복 처리 방지
        if (!canResolveDispute(match)) {
            return 0;
        }

        Post post = postService.getPostById(match.getPostId());

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(match.getApplicantId());

        int refundedPoint = 0;

        // Match 상태를 정상 완료로 전환
        match.completeByDispute();

        // 신청자 예치금 전액 반환
        userPointService.refundPoint(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );

        if (submitterIsApplicant) {
            refundedPoint = match.getApplicantDeposit();
        }

        // 정상 완료 흐름과 동일하게 후기 작성 마지막 날 알림 예약
        LocalDateTime reviewDeadlineReminderAt = match.getCompletedAt()
                .plusDays(7)
                .toLocalDate()
                .atTime(9, 0);

        redisTemplate.opsForZSet().add(
                ReviewRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(match.getId()),
                reviewDeadlineReminderAt.toEpochSecond(ZoneOffset.ofHours(9))
        );

        // 같은 Post에 아직 진행 중이거나 이의제기 중인 Match가 남아 있으면
        // 등록자 책임비는 아직 반환하지 않는다.
        boolean hasRemainingActiveMatch = matchRepository.findAllByPostId(match.getPostId())
                .stream()
                .anyMatch(this::canResolveDispute);

        if (hasRemainingActiveMatch) {
            return refundedPoint;
        }

        // 모든 활성 Match가 끝났다면 Post 완료 + 등록자 책임비 반환
        Post lockedPost = postService.getPostByIdWithLock(match.getPostId());

        if (lockedPost.getStatus() != PostStatus.COMPLETED) {
            lockedPost.complete();

            userPointService.refundPoint(
                    lockedPost.getAuthorId(),
                    lockedPost.getAuthorDeposit(),
                    lockedPost.getId()
            );

            if (submitterIsAuthor) {
                refundedPoint = lockedPost.getAuthorDeposit();
            }

            // 모든 매칭이 끝났으므로 채팅방 비활성화 예약
            chatService.scheduleChatRoomDeactivation(match.getPostId());
        }

        return refundedPoint;
    }

    // 관리자 PARTIALLY_ACCEPTED 판정
    // 노쇼 자체는 맞지만, 이의제기자의 사유가 일부 인정된 케이스다.
    // - 이의제기자: 50% 반환
    // - 피해 상대방: 100% 반환
    // - 포인트 정산은 Match 도메인에서 처리한다.
    //
    // restoredStatus:
    // - HOST_NO_SHOW  → 등록자 노쇼. 그룹에서는 같은 Post의 활성 Match 전체가 등록자 노쇼 처리된다.
    // - GUEST_NO_SHOW → 해당 신청자 Match 하나만 신청자 노쇼 처리된다.
    // - BOTH_NO_SHOW  → 해당 Match 하나만 양측 노쇼 처리된다.
    //
    // 반환값:
    // - 이의제기자에게 실제 반환된 포인트
    @Override
    @Transactional
    public int markNoShowByDispute(
            Long matchId,
            VerificationStatus restoredStatus,
            Long submitterId
    ) {

        Match match = getMatchById(matchId);

        if (!canResolveDispute(match)) {
            return 0;
        }

        Post post = postService.getPostById(match.getPostId());

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(match.getApplicantId());

        int refundedPoint = 0;

        if (restoredStatus == VerificationStatus.HOST_NO_SHOW) {

            // 등록자 노쇼는 Post 전체 책임이다.
            // 같은 Post의 MATCHED / DISPUTED 상태 Match 전체를 AUTHOR_NO_SHOW로 확정한다.
            List<Match> activeMatches = matchRepository.findAllByPostId(match.getPostId())
                    .stream()
                    .filter(this::canResolveDispute)
                    .toList();

            if (activeMatches.isEmpty()) {
                return 0;
            }

            // 등록자가 이의제기자라면 등록자 책임비 50% 반환
            if (submitterIsAuthor) {
                userPointService.partialRefundPoint(
                        post.getAuthorId(),
                        post.getAuthorDeposit(),
                        match.getId()
                );
                refundedPoint = post.getAuthorDeposit() / 2;
            }

            // 등록자 노쇼 피해자는 모든 활성 신청자다.
            // 모든 신청자 예치금은 전액 반환한다.
            for (Match activeMatch : activeMatches) {
                activeMatch.markNoShow(MatchStatus.AUTHOR_NO_SHOW);

                userPointService.refundPoint(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );

                if (submitterId.equals(activeMatch.getApplicantId())) {
                    refundedPoint = activeMatch.getApplicantDeposit();
                }
            }

            postService.completePost(match.getPostId());
            return refundedPoint;
        }

        if (restoredStatus == VerificationStatus.GUEST_NO_SHOW) {

            // 신청자 노쇼는 해당 Match 하나만 확정
            match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);

            if (submitterIsApplicant) {
                // 노쇼 당사자인 신청자의 사유가 일부 인정됨 → 50% 반환
                userPointService.partialRefundPoint(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
                refundedPoint = match.getApplicantDeposit() / 2;
            }

            // 등록자는 피해자이므로 전액 반환
            userPointService.refundPoint(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    match.getId()
            );

            if (submitterIsAuthor) {
                refundedPoint = post.getAuthorDeposit();
            }

        } else if (restoredStatus == VerificationStatus.BOTH_NO_SHOW) {

            // 양측 노쇼는 해당 Match 하나만 확정
            match.markNoShow(MatchStatus.BOTH_NO_SHOW);

            // BOTH_NO_SHOW에서 PARTIALLY_ACCEPTED는 이의제기자에게만 50% 반환한다.
            // 상대방은 여전히 노쇼 당사자이므로 여기서 피해자 전액 반환으로 처리하지 않는다.
            if (submitterIsAuthor) {
                userPointService.partialRefundPoint(
                        post.getAuthorId(),
                        post.getAuthorDeposit(),
                        match.getId()
                );
                refundedPoint = post.getAuthorDeposit() / 2;

            } else if (submitterIsApplicant) {
                userPointService.partialRefundPoint(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
                refundedPoint = match.getApplicantDeposit() / 2;
            }

        } else {

            // HOST/GUEST/BOTH_NO_SHOW 외 상태는 정상 플로우 아님
            return 0;
        }

        // GUEST_NO_SHOW / BOTH_NO_SHOW는 단건 처리 후,
        // 같은 Post에 아직 MATCHED / DISPUTED 상태 Match가 남아 있으면 Post 완료하지 않음
        boolean hasRemainingActiveMatch = matchRepository.findAllByPostId(match.getPostId())
                .stream()
                .anyMatch(this::canResolveDispute);

        if (!hasRemainingActiveMatch) {
            postService.completePost(match.getPostId());
        }

        return refundedPoint;
    }


    @Override
    @Transactional
    public void markAuthorNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 등록자(노쇼 당사자): 예치금 전액 몰수 (패널티)
        // 신청자(피해자): 예치금 전액 환급
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    @Transactional
    public void markApplicantNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 등록자(피해자): 예치금 전액 환급
        // 신청자(노쇼 당사자): 예치금 전액 몰수 (패널티)
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    @Transactional
    public void markBothNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.BOTH_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 양측 모두 노쇼 → 양측 예치금 전부 몰수
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }



    @Override
    @Transactional
    public CancelMatchResponseDto cancelMatch(Long matchId, Long userId, CancelMatchRequestDto request) {

        Match match = getMatchById(matchId);
        Post post = postService.getPostById(match.getPostId());

        // 당사자 검증
        if (!match.isParticipant(userId, post.getAuthorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }
        // 취소 가능 상태 검증
        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_INVALID_STATUS);
        }
        // 약속 시간 검증
        if (post.getMeetAt().isBefore(LocalDateTime.now())) {
            throw new MatchException(ErrorCode.MATCH_AFTER_MEET_TIME);
        }

        // 환불/몰수 금액 계산 — 취소자 50% 반환, 상대방 100% 환급
        boolean cancelerIsApplicant = match.isApplicant(userId);

        int cancelerDeposit = cancelerIsApplicant
                ? match.getApplicantDeposit()
                : post.getAuthorDeposit();
        int opponentDeposit = cancelerIsApplicant
                ? post.getAuthorDeposit()
                : match.getApplicantDeposit();

        int refundedPoint = cancelerDeposit / 2;
        int forfeitedPoint = cancelerDeposit - refundedPoint;

        // 취소자: 전체 예치금을 넘김 → partialRefundPoint가 내부에서 50% 계산
        userPointService.partialRefundPoint(userId, cancelerDeposit, matchId);

        // 상대방: 전액 환급
        Long opponentId = cancelerIsApplicant ? post.getAuthorId() : match.getApplicantId();
        userPointService.refundPoint(opponentId, opponentDeposit, matchId);

        match.cancel();
        post.decreaseCurrentApplicants(); // 참여 인원 감소

        // 매칭 취소되면 장소 데이터 삭제
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        if (cancelerIsApplicant) {
            // GUEST(신청자) 취소
            // 게시글이 MATCHED 상태였다면 OPEN으로 복구 (재신청 가능)
            if (post.isMatched()) {
                post.reopen();
            }
            // 채팅방 상태 ACTIVE 유지 — 취소한 신청자만 ChatMember에서 제거
            // 나머지 참여자(HOST + 다른 GUEST)는 계속 채팅 이용 가능
            chatService.removeChatMember(match.getPostId(), userId);

            Long chatRoomId = chatService.getChatRoomIdByPostId(match.getPostId());
            // 15. 그룹 채팅방 신청자 퇴장 알림 - 등록자에게만
            notificationPublisher.sendChatMemberLeft(post.getAuthorId(), chatRoomId);

        } else {

            // HOST(등록자) 취소 — 게시글 CANCELLED + 채팅방 완전 비활성화
            // BUG-05 수정 — 기존 코드는 match.getApplicantId() 단 1명만 환불
            // 그룹 매칭에서 GUEST가 여러 명이면 나머지는 환불되지 않는 치명적 버그
            // 해결: 해당 postId에 연결된 모든 MATCHED 상태 매칭을 조회해서 일괄 처리

            List<Match> allGuestMatches = matchRepository.findAllByPostIdAndStatus(
                    match.getPostId(), MatchStatus.MATCHED);
            // findAllByPostIdAndStatus: post_id = ? AND status = 'MATCHED' 인 매칭 전체 조회

            // 모든 GUEST에게 전액 환불 + 매칭 취소 상태 변경
            for (Match guestMatch : allGuestMatches) {
                // 각 GUEST의 예치금 전액 환불
                // HOST가 취소한 것이므로 GUEST 귀책 없음 → 전액 반환
                userPointService.refundPoint(
                        guestMatch.getApplicantId(),  // 환불받을 GUEST ID
                        guestMatch.getApplicantDeposit(), // 환불 금액 (GUEST가 예치한 금액)
                        guestMatch.getId()            // 포인트 거래 기록용 matchId
                );

                // 매칭 상태 CANCELLED로 변경
                guestMatch.cancel();
            }

            // HOST는 50% 환불 (HOST가 취소했으므로 패널티)
            userPointService.partialRefundPoint(userId, post.getAuthorDeposit(), matchId);

            // 게시글 상태 CANCELLED로 변경
            post.cancel();

            // 채팅방 비활성화
            chatService.deactivateChatRoom(match.getPostId());

            // 4. HOST가 매칭을 취소했을 때 - GUEST에게
            for (Match guestMatch : allGuestMatches) {
                // 각 GUEST에게 "HOST가 매칭을 취소했습니다" 알림
                notificationPublisher.sendHostCancelled(
                        guestMatch.getApplicantId(), // 알림 수신자 (GUEST)
                        guestMatch.getId()           // 관련 매칭 ID
                );
            }
        }

        // 3. GUEST가 신청을 취소했을 때 - HOST에게
        // GUEST가 취소한 경우에만 상대방(HOST)에게 알림 발송
        // HOST가 취소한 경우는 위 for문에서 이미 모든 GUEST에게 발송 완료
        if (cancelerIsApplicant) {
            notificationPublisher.sendGuestCancelled(opponentId, matchId);
        }

        // 매칭 취소 시 ZSet 알림 예약 제거
        String cancelMatchIdStr = String.valueOf(matchId);
        String cancelPostIdStr = String.valueOf(match.getPostId());

        // GUEST ZSet에서 해당 matchId 제거
        redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_GUEST, cancelMatchIdStr);
        redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_GUEST, cancelMatchIdStr);
        redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, cancelMatchIdStr);
        redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST, cancelMatchIdStr);
        redisTemplate.opsForZSet().remove(ReviewRedisZSetKeys.DEADLINE_REMINDER, cancelMatchIdStr);

        // HOST ZSet: 활성 신청자가 한 명도 없을 때만 postId 제거
        // match.cancel()이 이미 실행된 후 카운트하므로 본인은 CANCELLED로 빠져있음
        // → 0이면 진짜 마지막, 1 이상이면 다른 신청자 남아있음
        long activeMatchCount = matchRepository.countByPostIdAndStatus(match.getPostId(), MatchStatus.MATCHED);
        if (activeMatchCount <= 0) {
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST, cancelPostIdStr);
        }

        return CancelMatchResponseDto.of(
                match.getId(),
                match.getStatus(),
                refundedPoint,
                forfeitedPoint
        );
    }

    @Override
    public Match getMatchById(Long matchId) {

        return matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));
    }

    @Override
    public MatchInfoDto getMatchInfo(Long matchId) {

        Match match = getMatchById(matchId);

        return MatchInfoDto.from(match);
    }

    @Override
    public GetMatchResponseDto getMatch(Long matchId, Long currentUserId) {

        Match match = getMatchById(matchId);

        Post post = postService.getPostById(match.getPostId());

        if (!match.isParticipant(currentUserId, post.getAuthorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        Long chatRoomId = chatService.getChatRoomIdByPostId(match.getPostId());

        UserInfoDto authorInfo = userService.getUserInfo(post.getAuthorId());
        UserInfoDto applicantInfo = userService.getUserInfo(match.getApplicantId());

        return GetMatchResponseDto.of(
                match, post,
                authorInfo.nickname(), authorInfo.major(), authorInfo.studentNumber(),
                applicantInfo.nickname(), applicantInfo.major(), applicantInfo.studentNumber(),
                chatRoomId
        );
    }

    @Override
    public PageResponseDto<GetMatchesResponseDto> getMatches(

            Long userId, MatchStatus status, Pageable pageable
    ) {
        // 0. 매칭 목록 조회 (기존 그대로) — 쿼리 1번
        Page<Match> matchPage = (status == null)
                ? matchRepository.findAllByUserId(userId, pageable)
                : matchRepository.findAllByUserIdAndStatus(userId, status, pageable);

        // 현재 페이지의 실제 매칭 리스트 (ID 수집·룩업에 사용)
        List<Match> matches = matchPage.getContent();

        // 1: 게시글 정보를 벌크로 가져와 "상대방"을 계산

        // 1-1. 이번 페이지 매칭들의 postId만 중복 없이 추출
        List<Long> postIds = matches.stream()
                .map(Match::getPostId)
                .distinct()
                .toList();

        // 1-2. 게시글 정보를 IN 쿼리 1번으로
        Map<Long, PostMatchInfoDto> postMap = postService.getPostMatchInfos(postIds);

        // 1-3. 각 매칭마다 내가 author인지 판단 → 상대방 ID 결정
        Map<Long, Long> opponentIdByMatch = new java.util.HashMap<>();
        for (Match match : matches) {
            PostMatchInfoDto postInfo = postMap.get(match.getPostId());
            if (postInfo == null) continue; // 게시글이 없으면(이상 케이스) 스킵

            boolean isAuthor = postInfo.authorId().equals(userId);
            Long opponentId = isAuthor ? match.getApplicantId() : postInfo.authorId();
            opponentIdByMatch.put(match.getId(), opponentId);
        }

        // 2: 상대방 유저 + 채팅방을 각각 가져오기

        // 2-1. 상대방 ID 목록
        List<Long> opponentIds = opponentIdByMatch.values().stream()
                .distinct()
                .toList();

        // 2-2. 상대방 유저 정보 IN 쿼리 1번
        Map<Long, UserInfoDto> opponentMap = userService.getUserInfos(opponentIds);

        // 2-3. 채팅방 ID IN 쿼리 1번
        Map<Long, Long> chatRoomMap = chatService.getChatRoomIdsByPostIds(postIds);

        Page<GetMatchesResponseDto> dtoPage = matchPage.map(match -> {
            PostMatchInfoDto postInfo = postMap.get(match.getPostId());
            Long opponentId = opponentIdByMatch.get(match.getId());
            UserInfoDto opponentInfo = (opponentId != null) ? opponentMap.get(opponentId) : null;
            Long chatRoomId = chatRoomMap.get(match.getPostId());

            // 내 예치금 계산 — 내가 author면 authorDeposit, 아니면 applicantDeposit
            boolean isAuthor = (postInfo != null) && postInfo.authorId().equals(userId);
            int myDeposit = isAuthor ? postInfo.authorDeposit() : match.getApplicantDeposit();

            // 방어: 게시글/상대방 정보가 빠진 이상 케이스 (탈퇴 등) — null-safe 처리
            String oppNickname = (opponentInfo != null) ? opponentInfo.nickname() : null;
            String oppMajor    = (opponentInfo != null) ? opponentInfo.major() : null;
            String oppStudentNo= (opponentInfo != null) ? opponentInfo.studentNumber() : null;
            LocalDateTime meetAt = (postInfo != null) ? postInfo.meetAt() : null;
            String placeName     = (postInfo != null) ? postInfo.placeName() : null;
            int currentApplicants = (postInfo != null) ? postInfo.currentApplicants() : 0;
            int maxApplicants = (postInfo != null) ? postInfo.maxApplicants() : 0;

            return GetMatchesResponseDto.of(
                    match, opponentId,
                    oppNickname, oppMajor, oppStudentNo,
                    meetAt, placeName,
                    currentApplicants, maxApplicants,
                    myDeposit, isAuthor, chatRoomId
            );
        });

        return PageResponseDto.from(dtoPage);
    }

    @Override
    public Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds) {

        // [1] 빈 리스트 가드
        //     - null 체크: 호출 측의 실수 방지 (NPE 던지지 않고 빈 결과로 처리)
        //     - isEmpty 체크: IN 절에 빈 컬렉션을 넣으면 일부 DB(특히 Oracle)에서 SQL 문법 오류 발생
        //     - Collections.emptyMap()을 쓰는 이유: new HashMap<>()보다 불변/싱글톤이라 GC 부담 ↓
        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // [2] findAllById는 JpaRepository 기본 제공 메서드
        //     내부적으로 SELECT * FROM matches WHERE match_id IN (?, ?, ?, ...) 쿼리 1번으로 변환됨
        //     ※ 존재하지 않는 ID가 섞여 있으면 결과에서 그냥 빠짐 (예외 안 던짐)
        //        → 위 JavaDoc의 Contract와 자연스럽게 일치
        List<Match> matches = matchRepository.findAllById(matchIds);

        // [3] List<Match> → Map<Long, MatchInfoDto> 변환
        //     - keyMapper:   Match::getId         → Key로 사용할 값 추출 (matchId)
        //     - valueMapper: MatchInfoDto::from   → Value로 변환할 함수 (기존 단건 메서드와 동일한 변환기 재사용)
        //     ※ matchId는 PK라 중복될 수 없으므로 mergeFunction 인자는 불필요
        return matches.stream()
                .collect(Collectors.toMap(
                        Match::getId,
                        MatchInfoDto::from
                ));
    }

    // QR 스캔 성공 시 Match 단건만 COMPLETE 처리하는 메서드
    // 신청자 예치금 환급, Post 완료, 등록자 환급, 채팅방 비활성화 예약은 여기서 하지 않음
    @Override
    @Transactional
    public boolean completeSingleMatch(Long matchId) {

        // 동일 Match에 대한 동시 QR 스캔을 막기 위해 PESSIMISTIC_WRITE 락으로 조회
        Match match = matchRepository.findByIdWithLock(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));

        // 이미 완료된 Match에 대해서 중복 QR 스캔 방어
        if (match.getStatus() == MatchStatus.COMPLETED) {
            return false;
        }

        // QR 정상 완료는 MATCHED 상태에서만 가능
        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_INVALID_STATUS);
        }

        // Match 엔티티의 도메인 메서드로 MATCHED -> COMPLETED 상태 전이
        match.complete();

        // 신청자 예치금 환급
        // Meet 도메인에서 하지 않고 Match 도메인에서 처리
        userPointService.refundPoint(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );

        // 정상 만남 완료 후 후기 작성 마지막 날 알림 예약
        // 기존 completeMatch()에서 하던 역할을 completeSingleMatch()에도 반영
        LocalDateTime reviewDeadlineReminderAt = match.getCompletedAt()
                .plusDays(7)
                .toLocalDate()
                .atTime(9, 0);

        // 후기 작성 마지막 날 오전 9시에 알림 발송되도록 ZSet에 예약
        redisTemplate.opsForZSet().add(
                ReviewRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(match.getId()),
                reviewDeadlineReminderAt.toEpochSecond(ZoneOffset.ofHours(9))
        );

        // 아직 진행 중인 MATCHED가 남아 있으면 Post는 아직 완료하면 안 됨
        long remainingMatchedCount = matchRepository.countByPostIdAndStatus(match.getPostId(), MatchStatus.MATCHED);

        // 남은 MATCHED Match가 0개라면 이번 스캔이 해당 Post의 마지막 스캔
        return remainingMatchedCount == 0;
    }

    // 모든 활성 매칭이 종료된 뒤 Post 전체 COMPLETED 처리
    // Post COMPLETE 처리, 등록자 책임비 환급, 중복 호출되어도 한 번만 처리되도록 멱등성 보장
    @Override
    @Transactional
    public void completePostIfAllMatchesCompleted(Long postId) {

        // 방어 로직 -> 아직 QR 인증이 끝나지 않은 MATCHED 매칭이 있다면 Post를 완료하면 안 됨
        long remainingMatchedCount = matchRepository.countByPostIdAndStatus(postId, MatchStatus.MATCHED);

        // 남은 MATCHED 매칭이 있으면 아직 그룹 만남이 끝난 것이 아니므로 스킵
        if (remainingMatchedCount > 0) {
            return;
        }

        // 이미 PostService에 있는 PESSIMISTIC_WRITE 락 조회
        Post post = postService.getPostByIdWithLock(postId);

        // 멱등성 보장 -> 이미 완료된 Post면 등록자 환급도 다시 하지 않음
        if (post.getStatus() == PostStatus.COMPLETED) {
            return;
        }

        // Post 엔티티의 도메인 메서드로 상태를 COMPLETED로 전환
        post.complete();

        // 등록자 책임비는 그룹 만남 전체가 정상 종료된 시점에 한 번만 환급
        userPointService.refundPoint(
                post.getAuthorId(),
                post.getAuthorDeposit(),
                post.getId()
        );
    }

    // canFinalizeNoShow: 배치 노쇼 확정 처리 가능 여부
    // MATCHED(정상 매칭 중) 또는 DISPUTED(이의제기 기각 후 노쇼 확정 흐름) 두 케이스만 허용
    // 이미 COMPLETED/CANCELLED 등으로 종결된 건은 배치에서 재처리 방지
    private boolean canFinalizeNoShow(Match match) {
        return match.getStatus() == MatchStatus.MATCHED || match.getStatus() == MatchStatus.DISPUTED;
    }

    // canResolveDispute: 이의제기 판정 결과 처리 가능 여부
    // canFinalizeNoShow와 허용 조건은 동일하지만 호출 목적이 다름
    // → "노쇼 확정(배치)" vs "이의제기 판정 후처리(관리자)" 의도를 이름으로 구분
    private boolean canResolveDispute(Match match) {
        return match.getStatus() == MatchStatus.MATCHED || match.getStatus() == MatchStatus.DISPUTED;
    }
}
