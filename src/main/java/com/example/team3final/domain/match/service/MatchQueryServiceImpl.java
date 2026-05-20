package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchQueryServiceImpl implements MatchQueryService{

    private final MatchRepository matchRepository;
    private final PostQueryService postQueryService;
    private final ChatRoomRepository chatRoomRepository;

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
}
