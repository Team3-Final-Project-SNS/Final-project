package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.*;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService{

    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final UserPointService userPointService;
    private final UserService userService;
    private final PostService postService;

    @Override
    @Transactional
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {

        Post post = postService.getPostById(postId);

        // 1. 본인 소유 게시글 신청 차단
        if (post.getAuthorId().equals(applicantId)) {
            throw new MatchException(ErrorCode.MATCH_SELF_APPLY);
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

        // 3. 신청자 포인트 차감 — 잔액 부족 시 예외 → 트랜잭션 전체 롤백
        // matchId는 아직 생성 전이라 null
        userPointService.deductPoint(applicantId, post.getAuthorDeposit(), null);

        Match match = Match.builder()
                .postId(postId)
                .applicantId(applicantId)
                .applicantDeposit(post.getAuthorDeposit())
                .build();
        Match savedMatch = matchRepository.save(match);

        post.match();

        Long chatRoomId = chatService.createChatRoom(
                savedMatch.getId(),
                post.getAuthorId(),
                applicantId
        );

        // 양측 닉네임 조회
        String authorNickname = userService.getUserInfo(post.getAuthorId()).nickname();
        String applicantNickname = userService.getUserInfo(applicantId).nickname();

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
    @Transactional
    public void completeMatch(Long matchId) {

        Match match = getMatchById(matchId);

        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_INVALID_STATUS);
        }

        // Match 상태 → COMPLETED (엔티티가 status+completedAt 책임)
        match.complete();

        // Post 상태 → COMPLETED (Post 도메인에 위임)
        postService.completePost(match.getPostId());

        // 양측 100% 전액 환급
        Post post = postService.getPostById(match.getPostId());
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    @Transactional
    public void markAuthorNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        // MATCHED 아니면 스킵 (스케줄러 중단 방지 → 예외 대신 return)
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        match.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 등록자(노쇼 당사자) 몰수 / 신청자(피해자) 전액 환급
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    @Transactional
    public void markApplicantNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 등록자(피해자) 전액 환급 / 신청자(노쇼 당사자) 몰수
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    @Transactional
    public void markBothNoShow(Long matchId) {

        Match match = getMatchById(matchId);

        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        match.markNoShow(MatchStatus.BOTH_NO_SHOW);
        postService.completePost(match.getPostId());

        Post post = postService.getPostById(match.getPostId());
        // 양측 모두 몰수
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
        post.cancel();
        chatService.deactivateChatRoom(matchId);

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

        Long chatRoomId = chatRoomRepository.findByMatchId(matchId)
                .map(chatRoom -> chatRoom.getId())
                .orElse(null);

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
        Page<Match> matchPage = (status == null)
                ? matchRepository.findAllByUserId(userId, pageable)
                : matchRepository.findAllByUserIdAndStatus(userId, status, pageable);

        Page<GetMatchesResponseDto> dtoPage = matchPage.map(match -> {
            PostMatchInfoDto postMatchInfo = postService.getPostMatchInfo(match.getPostId()); // ⭐ postService

            boolean isAuthor = postMatchInfo.authorId().equals(userId);
            Long opponentId = isAuthor ? match.getApplicantId() : postMatchInfo.authorId();
            int myDeposit = isAuthor ? postMatchInfo.authorDeposit() : match.getApplicantDeposit();

            UserInfoDto opponentInfo = userService.getUserInfo(opponentId);
            Long chatRoomId = chatService.getChatRoomIdByMatchId(match.getId());

            return GetMatchesResponseDto.of(
                    match, opponentId,
                    opponentInfo.nickname(), opponentInfo.major(), opponentInfo.studentNumber(),
                    postMatchInfo.meetAt(), postMatchInfo.placeName(),
                    myDeposit, chatRoomId
            );
        });

        return PageResponseDto.from(dtoPage);
    }
}
