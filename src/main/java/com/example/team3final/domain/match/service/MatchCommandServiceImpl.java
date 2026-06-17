package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CancelMatchResponseDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.RedisPostService;
import com.example.team3final.domain.review.util.ReviewRedisZSetKeys;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Match 도메인의 생성/취소 등 사용자 요청 기반 변경 작업을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class MatchCommandServiceImpl implements MatchCommandService {

    private final MatchCreateService matchCreateService;
    private final ChatInternalService chatInternalService;
    private final MatchRepository matchRepository;
    private final UserPointService userPointService;
    private final PostInternalService postInternalService;
    private final UserLocationCleanupService userLocationCleanupService;
    private final NotificationPublisher notificationPublisher;
    private final UserInternalService userInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final RedisPostService redisPostService;

    @Override
    @Transactional(readOnly = true)
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {
        return matchCreateService.createMatch(postId, applicantId);
    }

    @Override
    public CancelMatchResponseDto cancelMatch(Long matchId, Long userId, CancelMatchRequestDto request) {

        Match match = matchRepository.findByIdWithLock(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));

        Post post = postInternalService.getPostById(match.getPostId());

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

        // 취소자가 신청자(GUEST)인지 등록자(HOST)인지 판별
        boolean cancelerIsApplicant = match.isApplicant(userId);
        // 1:1은 한 명 취소만으로 만남 성립 불가
        boolean isOneToOneMatch = post.getMaxApplicants() <= 2;

        // 응답 DTO 표시용 계산 (실제 포인트 처리 아님)
        // 취소자 예치금의 50%는 환급, 나머지 50%는 몰수
        int cancelerDeposit = cancelerIsApplicant
                ? match.getApplicantDeposit()
                : post.getAuthorDeposit();
        int refundedPoint = cancelerDeposit / 2;              // 취소자 환급분(50%)
        int forfeitedPoint = cancelerDeposit - refundedPoint; // 취소자 몰수분(50%)

        if (cancelerIsApplicant) {
            // ================================================================
            // GUEST(신청자) 취소
            // 정책: 해당 GUEST 예치금만 50% 몰수, HOST 예치금은 건드리지 않음
            //       나머지 신청자와 모임은 유지, 게시글 OPEN 복구
            // ================================================================

            // 1. 취소 GUEST만 50% 환급 (패널티 적용)
            //    HOST 예치금 환불 없음 -> 모임은 유지되므로 HOST 예치금은 계속 예치 상태
            userPointService.partialRefundApplicantDeposit(
                    userId,
                    match.getApplicantDeposit(),
                    matchId
            );

            if (isOneToOneMatch) {
                // 1:1 신청자 취소 시 등록자 예치금 전액 환불
                userPointService.refundAuthorDeposit(
                        post.getAuthorId(),
                        post.getAuthorDeposit(),
                        post.getId(),
                        "매칭 취소 환불"
                );
            }

            // 2. 매칭 상태 CANCELLED로 변경 + 참여 인원 감소
            match.cancel();
            post.decreaseCurrentApplicants();

            // 3. 취소 GUEST의 위치 데이터만 삭제
            //    HOST와 다른 GUEST의 위치 데이터는 모임이 유지되므로 건드리지 않음
            userLocationCleanupService.deleteLocationsByMatchId(matchId);

            if (isOneToOneMatch) {
                // 1:1 취소는 모집 재개가 아닌 매칭 종료
                post.cancel();
                publishPostVectorDeleteEvent(post.getId());
                chatInternalService.deactivateChatRoom(match.getPostId());
            } else if (post.isMatched()) {
                // 4. 게시글 MATCHED -> OPEN 복구
                //    정원이 미충족 상태로 돌아갔으므로 재신청 가능하도록 복구
                post.reopen();

                // 정원 취소로 다시 모집 가능해진 글은 AI 추천 후보에도 다시 반영합니다.
                publishPostVectorUpsertEvent(post);
            }

            // 5. 채팅방에서 해당 GUEST만 퇴장
            //    HOST + 나머지 GUEST는 채팅 계속 이용 가능
            if (!isOneToOneMatch) {
                chatInternalService.removeChatMember(match.getPostId(), userId);
            }

            // 6. HOST에게 "GUEST가 퇴장했습니다" 알림
            Long chatRoomId = chatInternalService.getChatRoomIdByPostId(match.getPostId());
            if (!isOneToOneMatch) {
                notificationPublisher.sendChatMemberLeft(post.getAuthorId(), chatRoomId);
            }

            // 7. HOST에게 "GUEST가 매칭을 취소했습니다" 알림
            notificationPublisher.sendGuestCancelled(post.getAuthorId(), matchId);

            // 8. 취소된 GUEST의 Redis 알림 예약 제거
            String cancelMatchIdStr = String.valueOf(matchId);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_GUEST, cancelMatchIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_GUEST, cancelMatchIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, cancelMatchIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST, cancelMatchIdStr);
            redisTemplate.opsForZSet().remove(ReviewRedisZSetKeys.DEADLINE_REMINDER, cancelMatchIdStr);

            // 9. HOST ZSet: 활성 신청자가 0명인 경우에만 제거
            //    match.cancel() 이후 카운트이므로 0이면 진짜 마지막 GUEST였던 것
            long activeMatchCount = matchRepository.countByPostIdAndStatus(match.getPostId(), MatchStatus.MATCHED);
            if (activeMatchCount <= 0 || isOneToOneMatch) {
                String cancelPostIdStr = String.valueOf(match.getPostId());
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_HOST, cancelPostIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_HOST, cancelPostIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, cancelPostIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST, cancelPostIdStr);
            }

        } else {
            // ================================================================
            // HOST(등록자) 취소
            // 정책: HOST 예치금 50% 몰수, 모든 GUEST 전액 환급, 게시글 CANCELLED
            // ================================================================

            // 핵심: 상태 변경 전에 MATCHED 게스트 목록 먼저 조회
            //    guestMatch.cancel() 이후 조회하면 JPA flush 타이밍에 따라
            //    방금 취소한 match가 결과에서 빠질 수 있으므로 반드시 먼저 SELECT
            //    cancelMatch()를 실행한 원래 match도 MATCHED 상태이므로 여기에 포함됨
            //    아래 반복문 안에서 원래 match도 GUEST 몫까지 환급/알림/취소 처리됨
            List<Match> allGuestMatches = matchRepository.findAllByPostIdAndStatus(
                    match.getPostId(), MatchStatus.MATCHED
            );

            // 1. HOST 50% 환급 (HOST가 취소했으므로 패널티 적용), 정확히 한 번만 실행
            userPointService.partialRefundAuthorDeposit(
                    userId,
                    post.getAuthorDeposit(),
                    post.getId()
            );

            // 2. 모든 GUEST 처리 -> 전액 환급 + 위치 삭제 + Redis 정리 + 알림 + 상태 취소
            for (Match guestMatch : allGuestMatches) {

                // GUEST는 귀책 없으므로 예치금 100% 반환
                userPointService.refundApplicantDeposit(
                        guestMatch.getApplicantId(),      // 환불받을 GUEST ID
                        guestMatch.getApplicantDeposit(), // 환불 금액 (GUEST 예치금 전액)
                        guestMatch.getId()                // 포인트 거래 기록용 matchId
                );

                // 각 GUEST의 위치 데이터 삭제
                // HOST 취소 시 모든 match에 대한 위치 데이터를 정리해야 하므로 반복문 안 처리
                userLocationCleanupService.deleteLocationsByMatchId(guestMatch.getId());

                // 각 GUEST의 Redis 알림 예약 제거
                String guestMatchIdStr = String.valueOf(guestMatch.getId());
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_GUEST, guestMatchIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_GUEST, guestMatchIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, guestMatchIdStr);
                redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST, guestMatchIdStr);
                redisTemplate.opsForZSet().remove(ReviewRedisZSetKeys.DEADLINE_REMINDER, guestMatchIdStr);

                // 각 GUEST에게 "HOST가 매칭을 취소했습니다" 알림
                notificationPublisher.sendHostCancelled(
                        guestMatch.getApplicantId(),
                        guestMatch.getId()
                );

                // GUEST 매칭 상태 CANCELLED로 변경
                // allGuestMatches에 cancelMatch()를 실행한 원래 match도 포함되므로
                // 반복문 바깥에서 별도로 match.cancel()을 호출하지 않음
                guestMatch.cancel();
            }

            // 3. 게시글 상태 CANCELLED로 변경
            post.cancel();

            // 등록자가 취소한 모집글은 더 이상 추천되지 않도록 벡터 인덱스에서 제거합니다.
            publishPostVectorDeleteEvent(post.getId());

            // 4. 채팅방을 DEACTIVATED 상태로 전환하여 메시지 전송/조회를 모두 차단
            //    ChatMember 레코드는 삭제하지 않으며, 상태 전환으로 접근을 제어함
            chatInternalService.deactivateChatRoom(match.getPostId());

            // 5. HOST ZSet 제거 -> HOST가 취소했으므로 postId 기준 모든 예약 정리
            String cancelPostIdStr = String.valueOf(match.getPostId());
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_30_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_15_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, cancelPostIdStr);
            redisTemplate.opsForZSet().remove(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST, cancelPostIdStr);
        }

        redisPostService.evictPostLists();

        return CancelMatchResponseDto.of(
                match.getId(),
                match.getStatus(),
                refundedPoint,
                forfeitedPoint
        );
    }

    private void publishPostVectorUpsertEvent(Post post) {
        if (applicationEventPublisher == null || post == null || !post.isOpen() || post.isDeleted()) {
            return;
        }

        // 이벤트에는 추천 검색에 필요한 스냅샷만 담습니다.
        // Listener가 AFTER_COMMIT에서 embedding 생성과 PostgreSQL upsert를 비동기로 수행합니다.
        applicationEventPublisher.publishEvent(
                new PostVectorUpsertEvent(
                        post.getId(),
                        post.getAuthorId(),
                        userInternalService.findUserById(post.getAuthorId()).getUniversityId(),
                        post.getStatus(),
                        post.getMeetAt(),
                        post.getPlaceName(),
                        post.getContent(),
                        post.getAuthorDeposit(),
                        post.getMaxApplicants(),
                        post.getCurrentApplicants(),
                        post.getPlaceLat(),
                        post.getPlaceLng()
                )
        );
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        if (applicationEventPublisher == null || postId == null) {
            return;
        }

        // 상태가 OPEN이 아니게 된 게시글은 postId만 알면 벡터 인덱스에서 제거할 수 있습니다.
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
