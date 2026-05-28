package com.example.team3final.domain.admin.meet.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMeetVerificationServiceImpl implements AdminMeetVerificationService {

    private final MeetVerificationService meetVerificationService;
    private final MatchService matchService;
    private final PostService postService;
    private final UserService userService;
    private final DisputeService disputeService;

    // 노쇼 후보군 조회
    @Override
    public PageResponseDto<AdminNoShowCandidateResponseDto> getNoShowCandidates(Pageable pageable) {

        // 노쇼 상태 MeetVerification 페이징 조회
        Page<MeetVerification> meetVerificationPage = meetVerificationService.getNoShowCandidates(pageable);

        // matchId 목록 추출
        List<Long> matchIds = meetVerificationPage.getContent()
                .stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 배치 조회
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchIds);

        // MatchInfoDto 에서 postId 추출
        List<Long> postIds = matchInfoDtoMap.values()
                .stream()
                .map(MatchInfoDto::postId)
                .distinct()
                .toList();

        // postId -> PostInfoDto 맵
        Map<Long, PostInfoDto> postInfoByPostId = postService.getPostInfos(postIds);

        // authorId + applicantId 목록 수집
        List<Long> userIds = new ArrayList<>();
        // authorId -> HOST
        postInfoByPostId.values().forEach(p -> userIds.add(p.authorId()));
        // applicantId -> GUEST
        matchInfoDtoMap.values().forEach(m -> userIds.add(m.applicantId()));

        // 닉네임 배치 조회
        Map<Long, String> nicknameMap = userService.getUserNicknameMap(
                userIds.stream().distinct().toList());

        // matchId 목록으로 이의제기 존재 여부를 한 번에 조회 (N+1 방지)
        Set<Long> matchIdsWithDispute = disputeService.getMatchIdsWithDispute(matchIds);

        // DTO 조립
        Page<AdminNoShowCandidateResponseDto> result = meetVerificationPage.map(meetVerification -> {

            // matchId로 Match 정보 조회
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(meetVerification.getMatchId());

            // matchId로 Match를 찾은 뒤 postId로 Post를 찾음
            PostInfoDto postInfoDto = (matchInfoDto != null)
                    ? postInfoByPostId.get(matchInfoDto.postId())
                    : null;

            // 데이터 정합성 문제로 Match 또는 Post가 없는 경우 방어 로직
            if (matchInfoDto == null || postInfoDto == null) {
                throw new AdminException(ErrorCode.MATCH_NOT_FOUND);
            }

            // 닉네임 조회 -> null이면 유저 데이터 없음 -> 예외
            String hostNickname = nicknameMap.get(postInfoDto.authorId());
            if (hostNickname == null) {
                throw new AdminException(ErrorCode.USER_NOT_FOUND);
            }
            String guestNickname = nicknameMap.get(matchInfoDto.applicantId());
            if (guestNickname == null) {
                throw new AdminException(ErrorCode.USER_NOT_FOUND);
            }

            // Set.contains() → 이 매칭에 이의제기가 있으면 true
            boolean hasDispute = matchIdsWithDispute.contains(meetVerification.getMatchId());

            return AdminNoShowCandidateResponseDto.of(
                    meetVerification,
                    hostNickname,
                    guestNickname,
                    postInfoDto.meetAt(),
                    hasDispute
            );
        });

        return PageResponseDto.from(result);
    }
}
