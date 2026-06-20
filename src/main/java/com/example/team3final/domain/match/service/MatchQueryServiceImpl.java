package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.dto.response.MatchParticipantDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchQueryServiceImpl implements MatchQueryService {

    private final MatchRepository matchRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final ChatInternalService chatInternalService;
    private final UserInternalService userInternalService;
    private final MeetVerificationInternalService meetVerificationInternalService;

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
        // 상세 응답용 postId 기준 전체 참여자 수집
        List<Match> postMatches = matchRepository.findAllByPostId(match.getPostId());
        List<Long> participantUserIds = postMatches.stream()
                .filter(this::isVisibleParticipantMatch)
                .map(Match::getApplicantId)
                .distinct()
                .toList();
        Map<Long, UserInfoDto> participantUserInfoMap = participantUserIds.isEmpty()
                ? Collections.emptyMap()
                : userInternalService.getUserInfos(participantUserIds);
        List<MatchParticipantDto> participants = buildParticipants(
                post.getAuthorId(),
                authorInfo,
                postMatches,
                participantUserInfoMap
        );
        LocalDateTime meetAt = meetVerificationInternalService.findEffectiveExtendedMeetAtByMatchId(matchId)
                .orElse(post.getMeetAt());

        return GetMatchResponseDto.of(
                match, post,
                authorInfo.nickname(), authorInfo.major(), authorInfo.studentNumber(),
                applicantInfo.nickname(), applicantInfo.major(), applicantInfo.studentNumber(),
                authorInfo.mannerTemperature(),
                participants,
                meetAt,
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

        List<Match> matches = matchPage.getContent();
        List<Long> postIds = matches.stream()
                .map(Match::getPostId)
                .distinct()
                .toList();

        Map<Long, PostMatchInfoDto> postMap = postInternalService.getPostMatchInfos(postIds);
        // 그룹 매칭 목록용 postId별 신청자 묶음
        Map<Long, List<Match>> matchesByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : matchRepository.findAllByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(Match::getPostId));

        List<Long> userIds = new ArrayList<>();
        postMap.values().stream()
                .map(PostMatchInfoDto::authorId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        matchesByPostId.values().stream()
                .flatMap(List::stream)
                .filter(this::isVisibleParticipantMatch)
                .map(Match::getApplicantId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        Map<Long, UserInfoDto> userInfoMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userInternalService.getUserInfos(userIds.stream().distinct().toList());

        Map<Long, Long> chatRoomMap = chatInternalService.getChatRoomIdsByPostIds(postIds);
        List<Long> matchIds = matches.stream().map(Match::getId).toList();
        Map<Long, LocalDateTime> extendedMeetAtMap = matchIds.isEmpty()
                ? Collections.emptyMap()
                : meetVerificationInternalService.findExtendedMeetAtMapByMatchIds(matchIds);

        Map<Long, List<MatchParticipantDto>> participantsByPostId = new HashMap<>();
        // postId별 작성자 1명 + 취소되지 않은 신청자 목록 변환
        for (Long postId : postIds) {
            PostMatchInfoDto postInfo = postMap.get(postId);
            if (postInfo == null) {
                continue;
            }
            UserInfoDto authorInfo = userInfoMap.get(postInfo.authorId());
            participantsByPostId.put(
                    postId,
                    buildParticipants(
                            postInfo.authorId(),
                            authorInfo,
                            matchesByPostId.getOrDefault(postId, Collections.emptyList()),
                            userInfoMap
                    )
            );
        }

        Page<GetMatchesResponseDto> dtoPage = matchPage.map(match -> {
            PostMatchInfoDto postInfo = postMap.get(match.getPostId());
            boolean isAuthor = postInfo != null && postInfo.authorId().equals(userId);
            Long opponentId = isAuthor
                    ? match.getApplicantId()
                    : (postInfo != null ? postInfo.authorId() : null);
            UserInfoDto opponentInfo = opponentId != null ? userInfoMap.get(opponentId) : null;
            Long chatRoomId = chatRoomMap.get(match.getPostId());

            int myDeposit = isAuthor
                    ? (postInfo != null ? postInfo.authorDeposit() : 0)
                    : match.getApplicantDeposit();
            LocalDateTime meetAt = extendedMeetAtMap.getOrDefault(
                    match.getId(),
                    postInfo != null ? postInfo.meetAt() : null
            );
            List<MatchParticipantDto> participants = participantsByPostId.getOrDefault(
                    match.getPostId(),
                    Collections.emptyList()
            );

            return GetMatchesResponseDto.of(
                    match,
                    opponentId,
                    opponentInfo != null ? opponentInfo.nickname() : null,
                    opponentInfo != null ? opponentInfo.major() : null,
                    opponentInfo != null ? opponentInfo.studentNumber() : null,
                    meetAt,
                    postInfo != null ? postInfo.placeName() : null,
                    postInfo != null ? postInfo.currentApplicants() : 0,
                    postInfo != null ? postInfo.maxApplicants() : 0,
                    myDeposit,
                    isAuthor,
                    participants,
                    postInfo != null ? postInfo.status() : null,
                    chatRoomId
            );
        });

        return PageResponseDto.from(dtoPage);
    }

    private List<MatchParticipantDto> buildParticipants(
            Long authorId,
            UserInfoDto authorInfo,
            List<Match> matches,
            Map<Long, UserInfoDto> userInfoMap
    ) {
        List<MatchParticipantDto> participants = new ArrayList<>();
        // 작성자는 match row가 없으므로 Post authorId와 UserInfo로 별도 추가
        if (authorId != null && authorInfo != null) {
            participants.add(MatchParticipantDto.author(authorId, authorInfo));
        }

        matches.stream()
                .filter(this::isVisibleParticipantMatch)
                .forEach(match -> {
                    UserInfoDto applicantInfo = userInfoMap.get(match.getApplicantId());
                    if (applicantInfo != null) {
                        participants.add(MatchParticipantDto.applicant(match, applicantInfo));
                    }
                });

        return participants;
    }

    private boolean isVisibleParticipantMatch(Match match) {
        // 취소된 신청자는 현재 모임 참여자 목록에서 제외
        return match.getStatus() != MatchStatus.CANCELLED;
    }
}
