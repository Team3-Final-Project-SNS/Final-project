package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Match 도메인의 조회 전용 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchQueryServiceImpl implements MatchQueryService {

    private final MatchRepository matchRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final ChatInternalService chatInternalService;
    private final UserInternalService userInternalService;
    private final MeetVerificationRepository meetVerificationRepository;

    @Override
    public GetMatchResponseDto getMatch(Long matchId, Long currentUserId) {

        Match match = matchInternalService.getMatchById(matchId);

        Post post = postInternalService.getPostById(match.getPostId());

        if (!match.isParticipant(currentUserId, post.getAuthorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        Long chatRoomId = chatInternalService.getChatRoomIdByPostId(match.getPostId());

        UserInfoDto authorInfo = userInternalService.getUserInfo(post.getAuthorId());
        UserInfoDto applicantInfo = userInternalService.getUserInfo(match.getApplicantId());
        LocalDateTime meetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByMatchId(matchId)
                .orElse(post.getMeetAt());

        return GetMatchResponseDto.of(
                match, post,
                authorInfo.nickname(), authorInfo.major(), authorInfo.studentNumber(),
                applicantInfo.nickname(), applicantInfo.major(), applicantInfo.studentNumber(),
                authorInfo.mannerTemperature(),
                meetAt,
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
        Map<Long, PostMatchInfoDto> postMap = postInternalService.getPostMatchInfos(postIds);

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
        Map<Long, UserInfoDto> opponentMap = userInternalService.getUserInfos(opponentIds);

        // 2-3. 채팅방 ID IN 쿼리 1번
        Map<Long, Long> chatRoomMap = chatInternalService.getChatRoomIdsByPostIds(postIds);
        List<Long> matchIds = matches.stream().map(Match::getId).toList();
        Map<Long, LocalDateTime> extendedMeetAtMap = matchIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : meetVerificationRepository.findExtendedMeetAtRowsByMatchIds(matchIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (LocalDateTime) row[1],
                        (first, second) -> second
                ));

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
            LocalDateTime meetAt = extendedMeetAtMap.getOrDefault(
                    match.getId(),
                    (postInfo != null) ? postInfo.meetAt() : null
            );
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
}
