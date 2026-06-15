package com.example.team3final.domain.meet.service.support;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.context.MeetVerificationBulkContext;
import com.example.team3final.domain.meet.context.MeetVerificationContext;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 조회 보조 컴포넌트
@Component
@RequiredArgsConstructor
public class MeetVerificationContextReader {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;

    public MeetVerificationContext loadMeetContext(Long matchId) {
        // matchId로 MeetVerification 조회, 없으면 예외
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));
        // matchId로 매칭 정보 조회
        MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);
        // 매칭 정보에서 postId를 꺼내 게시글 정보 조회
        PostInfoDto postInfo = postInternalService.getPostInfo(matchInfo.postId());
        return new MeetVerificationContext(meetVerification, matchInfo, postInfo);
    }

    public MeetVerificationBulkContext loadBulkMatchContext(List<Long> matchIds) {
        // Match 도메인 벌크 조회 (N+1 방지)
        Map<Long, MatchInfoDto> matchInfoMap = matchInternalService.getMatchInfos(matchIds);
        // Match에서 postId만 뽑아서 Post 도메인 벌크 조회
        List<Long> postIds = matchInfoMap.values().stream()
                .map(MatchInfoDto::postId)
                .distinct()
                .toList();
        Map<Long, PostInfoDto> postInfoMap = postInternalService.getPostInfos(postIds);
        return new MeetVerificationBulkContext(matchInfoMap, postInfoMap);
    }

    // 매칭 당사자 검증
    // 사용 위치: createPlaceVerification, getMeetVerification, createMeetExtension,
    //            acceptMeetExtension, rejectMeetExtension, getMeetExtension
    public void validateParticipant(Long userId, MatchInfoDto matchInfo, PostInfoDto postInfo) {
        // 등록자(authorId) 또는 신청자(applicantId)가 아니면 예외
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }
    }

    // 같은 Post에 속한 활성 Match ID 목록을 조회
    // 활성 Match란 현재 만남이 진행 중인 MATCHED 상태의 Match를 의미
    public List<Long> getActiveMatchIdsByPostId(Long postId) {

        // 같은 Post에 속한 모든 Match ID를 조회
        List<Long> siblingMatchIds = matchInternalService.getMatchIdsByPostId(postId);

        // Match ID가 없으면 빈 리스트를 반환
        if (siblingMatchIds.isEmpty()) {
            return List.of();
        }

        // Match 정보를 벌크 조회
        Map<Long, MatchInfoDto> siblingMatchInfoMap =
                matchInternalService.getMatchInfos(siblingMatchIds);

        // MATCHED 상태인 Match ID만 필터링해서 반환
        return siblingMatchInfoMap.entrySet().stream()
                .filter(entry -> entry.getValue().status() == MatchStatus.MATCHED)
                .map(Map.Entry::getKey)
                .toList();
    }
}
