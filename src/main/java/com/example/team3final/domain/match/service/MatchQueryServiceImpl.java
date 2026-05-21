package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostQueryService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchQueryServiceImpl implements MatchQueryService{

    private final MatchRepository matchRepository;
    private final PostQueryService postQueryService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserService userService;
    private final ChatService chatService;

    // TODO: User 도메인 머지 후 활성화
    // private final UserQueryService userQueryService;


    @Override
    public Match getMatchById(Long matchId) {

        return matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));
    }

    @Override
    public MatchInfoDto getMatchInfo(Long matchId) {
        // getMatchById 재사용 → NotFound 처리 한 곳에 통합
        Match match = getMatchById(matchId);
        return MatchInfoDto.from(match);
    }

    @Override
    public GetMatchResponseDto getMatch(Long matchId, Long currentUserId) {

        // 1. 매칭 존재 확인
        Match match = getMatchById(matchId);

        // 2. 등록자 ID 확보를 위해 Post 조회
        Post post = postQueryService.getPostById(match.getPostId());

        // 3. 매칭 당사자 검증
        if (!match.isParticipant(currentUserId, post.getAuthorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 4. 채팅방 ID 조회
        Long chatRoomId = chatRoomRepository.findByMatchId(matchId)
                .map(chatRoom -> chatRoom.getId())
                .orElse(null);

        // 5. User 도메인에서 양측 정보 조회
        // TODO: User 도메인 머지 후 활성화
        // User author = userQueryService.getUserById(post.getAuthorId());
        // User applicant = userQueryService.getUserById(match.getApplicantId());
        // String authorNickname = author.getNickname();
        // String authorMajor = author.getMajor();
        // String authorStudentNumber = author.getStudentNumber();
        // String applicantNickname = applicant.getNickname();
        // String applicantMajor = applicant.getMajor();
        // String applicantStudentNumber = applicant.getStudentNumber();
        //
        String authorNickname = "임시-등록자";
        String authorMajor = "임시-등록자학과";
        String authorStudentNumber = "00";
        String applicantNickname = "임시-신청자";
        String applicantMajor = "임시-신청자학과";
        String applicantStudentNumber = "00";

        return GetMatchResponseDto.of(
                match,
                post,
                authorNickname,
                authorMajor,
                authorStudentNumber,
                applicantNickname,
                applicantMajor,
                applicantStudentNumber,
                chatRoomId
        );
    }

    @Override
    public PageResponseDto<GetMatchesResponseDto> getMatches(
            Long userId,
            MatchStatus status,
            Pageable pageable
    ) {
        // 내 매칭 목록 조회 (등록자 or 신청자)
        Page<Match> matchPage = (status == null)
                ? matchRepository.findAllByUserId(userId, pageable)
                : matchRepository.findAllByUserIdAndStatus(userId, status, pageable);

        // Page<Match> → Page<GetMatchesItemResponseDto> 변환
        Page<GetMatchesResponseDto> dtoPage = matchPage.map(match -> {

            // Post 정보 조회
            PostMatchInfoDto postMatchInfo = postQueryService.getPostMatchInfo(match.getPostId());

            // 내 역할 판단 → opponentId, myDeposit 결정
            boolean isAuthor = postMatchInfo.authorId().equals(userId);
            Long opponentId = isAuthor ? match.getApplicantId() : postMatchInfo.authorId();
            int myDeposit = isAuthor ? postMatchInfo.authorDeposit() : match.getApplicantDeposit();

            UserInfoDto opponentInfo = userService.getUserInfo(opponentId);

            Long chatRoomId = chatService.getChatRoomIdByMatchId(match.getId());

            return GetMatchesResponseDto.of(
                    match,
                    opponentId,
                    opponentInfo.nickname(),
                    opponentInfo.major(),
                    opponentInfo.studentNumber(),
                    postMatchInfo.meetAt(),
                    postMatchInfo.placeName(),
                    myDeposit,
                    chatRoomId
            );
        });

        // PageResponseDto로 래핑
        return PageResponseDto.from(dtoPage);
    }
}
