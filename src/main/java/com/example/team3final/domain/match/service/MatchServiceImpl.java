package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.*;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        // 2-1. 중복 신청 차단 (같은 게시글에 같은 사용자 두 번 신청 불가)
        //   그룹 매칭에서 의미가 생김 — 1:1에선 본인 게시글 차단(2-1)에 흡수되지만,
        //   그룹에선 별도 신청자가 두 번 들어오는 걸 막아야 함.
        //   MATCHED/CANCELLED 등 상태 무관하게 같은 (postId, applicantId) 조합 존재 여부만 봄
        //   → 취소 후 재신청을 막을지는 정책 확인 필요. 일단 막는 방향.
        if (matchRepository.existsByPostIdAndApplicantId(postId, applicantId)) {
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

        // 모집 완료 시 채팅방 생성 및 게시글 상태 MATCHED 전환
        Long chatRoomId = null;
        if (post.isFull()) {
            post.match();
            chatRoomId = chatService.createChatRoom(
                    postId,
                    post.getAuthorId(),
                    applicantId
            );
        }

        // 양측 닉네임 조회
        String authorNickname = userService.getUserInfo(post.getAuthorId()).nickname();
        String applicantNickname = userService.getUserInfo(applicantId).nickname();

        // 1번 알림 - 게시글 작성자에게 신청 알림 발송
        notificationPublisher.sendMatchApplied(post.getAuthorId(), savedMatch.getId());

        // 16번 알림 - 신청자에게 매칭 확정 알림 발송
        notificationPublisher.sendMatchConfirmed(applicantId, savedMatch.getId());


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
        post.decreaseCurrentApplicants(); // 참여 인원 감소
        if (cancelerIsApplicant) {
            // GUEST(신청자) 취소 — 게시글은 OPEN으로 복구, 채팅방은 유지
            // post 상태가 MATCHED였다면 OPEN으로 되돌림 (정원이 비었으니 다시 받아야 함)
            if (post.isMatched()) {
                post.reopen();  // ★ Post 엔티티에 reopen() 메서드 새로 추가 필요
            }
            // 채팅방 비활성화 X — 그룹 매칭이면 나머지 사람들 채팅 유지
            chatService.deactivateChatRoom(match.getPostId());
        } else {
            // HOST(작성자) 취소 — 게시글 CANCELLED + 채팅방 비활성화
            post.cancel();
            chatService.deactivateChatRoom(match.getPostId());
        }

        // 2번 알림 - 상대방에게 매칭 취소 알림 발송
        notificationPublisher.sendMatchCancelled(opponentId, matchId);

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
                : matchRepository.findAllByUserIdAndStatus(userId, status.name(), pageable);

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

        // 2-3. 매칭 ID 목록
        List<Long> matchIds = matches.stream()
                .map(Match::getId)
                .toList();

        // 2-4. 채팅방 ID IN 쿼리 1번
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

            return GetMatchesResponseDto.of(
                    match, opponentId,
                    oppNickname, oppMajor, oppStudentNo,
                    meetAt, placeName,
                    myDeposit, chatRoomId
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
}
