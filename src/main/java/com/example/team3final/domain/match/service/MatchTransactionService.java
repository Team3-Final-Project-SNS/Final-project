package com.example.team3final.domain.match.service;
// ====================================================================
// MatchTransactionService.java
//
// 역할: 매칭 생성 트랜잭션 전담
//
// Self-invocation 문제 해결 구조:
//   MatchCreateService.createMatch()       ← 락 관리, @Transactional 없음
//       ↓ 외부 빈 호출 (Self-invocation 없음)
//   MatchTransactionService.createMatchInTransaction()  ← @Transactional(REQUIRES_NEW)
//
// 두 메서드가 다른 클래스에 있으므로
// Spring AOP 프록시가 정상 동작하여 @Transactional이 적용됨
// ====================================================================

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MatchTransactionService {

    private final MatchRepository matchRepository;
    private final PostService postService;
    private final ChatService chatService;
    private final UserPointService userPointService;
    private final UserService userService;
    private final ReviewAvoidanceService reviewAvoidanceService;
    private final ReportService reportService;
    private final StringRedisTemplate redisTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateMatchResponseDto createMatchInTransaction(Long postId, Long applicantId) {

        // SELECT FOR UPDATE로 Post 행 잠금
        // → 한 스레드가 읽고 수정하는 동안 다른 스레드는 대기
        // → @Version 낙관락 충돌 없이 항상 최신 상태를 읽음
        Post post = postService.getPostWithPessimisticLock(postId);

        // 1. 본인 소유 게시글 신청 차단
        if (post.getAuthorId().equals(applicantId)) {
            throw new MatchException(ErrorCode.MATCH_SELF_APPLY);
        }

        // 다시 만나고 싶지 않아요 관계 차단
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
        if (reportService.existsPendingReport(post.getId())) {
            throw new MatchException(ErrorCode.MATCH_POST_UNDER_REPORT);
        }

        // 2-1. 중복 신청 차단 (MATCHED 상태만 중복으로 판단, CANCELLED는 재신청 허용)
        if (matchRepository.existsByPostIdAndApplicantIdAndStatus(
                postId, applicantId, MatchStatus.MATCHED)) {
            throw new MatchException(ErrorCode.MATCH_DUPLICATE_APPLY);
        }

        // 2-2. 정원 초과 차단
        if (post.isFull()) {
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        }

        // 3. 신청자 포인트 차감 — 잔액 부족 시 예외 → 트랜잭션 전체 롤백
        userPointService.deductPoint(applicantId, post.getAuthorDeposit(), null);

        // 4. Match 엔티티 생성 및 저장
        Match match = Match.builder()
                .postId(postId)
                .applicantId(applicantId)
                .applicantDeposit(post.getAuthorDeposit())
                .build();
        Match savedMatch = matchRepository.save(match);

        // 5. 참여 인원 증가
        post.increaseCurrentApplicants();

        // 6. 채팅방 생성 또는 기존 채팅방에 멤버 추가
        Long chatRoomId;
        if (!chatService.existsChatRoomByPostId(postId)) {
            // 첫 번째 신청자 → HOST + GUEST 채팅방 신규 생성
            chatRoomId = chatService.createChatRoom(postId, post.getAuthorId(), applicantId);
        } else {
            // 두 번째 이후 → 기존 채팅방에 GUEST만 추가
            chatService.addChatMember(postId, applicantId);
            chatRoomId = chatService.getChatRoomIdByPostId(postId);
        }

        // 7. 정원이 다 찼을 때만 게시글 상태를 MATCHED로 전환
        if (post.isFull()) {
            post.match();
        }

        // 8. 응답 DTO에 필요한 닉네임 조회
        String authorNickname = userService.getUserInfo(post.getAuthorId()).nickname();
        String applicantNickname = userService.getUserInfo(applicantId).nickname();

        // 9. 만남 알림 ZSet 예약 (30분/15분/5분 전, 10분 경과)
        LocalDateTime meetAt = post.getMeetAt();
        LocalDateTime now = LocalDateTime.now();
        ZoneOffset KST = ZoneOffset.ofHours(9);
        String postIdStr = String.valueOf(postId);
        String matchIdStr = String.valueOf(savedMatch.getId());

        if (now.isBefore(meetAt.minusMinutes(30))) {
            double score = meetAt.minusMinutes(30).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_30_HOST, postIdStr, score);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_30_GUEST, matchIdStr, score);
        }
        if (now.isBefore(meetAt.minusMinutes(15))) {
            double score = meetAt.minusMinutes(15).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_15_HOST, postIdStr, score);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_15_GUEST, matchIdStr, score);
        }
        if (now.isBefore(meetAt.minusMinutes(5))) {
            double score = meetAt.minusMinutes(5).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, postIdStr, score);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, matchIdStr, score);
        }
        if (now.isBefore(meetAt.plusMinutes(10))) {
            double score = meetAt.plusMinutes(10).toEpochSecond(KST);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST, postIdStr, score);
            redisTemplate.opsForZSet().add(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST, matchIdStr, score);
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
}