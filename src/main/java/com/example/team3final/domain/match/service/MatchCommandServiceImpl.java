package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchCommandServiceImpl implements MatchCommandService{

    private final MatchRepository matchRepository;

    // 같은 도메인이 아닌 Post 도메인의 Query 서비스 의존
    // → 도메인 경계 유지: Match는 PostRepository를 직접 알지 못함, Post Service를 통해서만 접근
    private final PostQueryService postQueryService;

    // TODO: User 도메인 머지 후 활성화
    // private final UserCommandService userCommandService;   // 포인트 차감
    // private final UserQueryService userQueryService;       // 닉네임 조회
    //
    // TODO: Chat 도메인 머지 후 활성화
    // private final ChatRoomCommandService chatRoomCommandService;  // 채팅방 자동 생성

    @Override
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {

        Post post = postQueryService.getPostById(postId);

        // 비즈니스 검증

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

        // 3. 신청자 포인트 차감 (등록자와 동일 금액)
        // 명세서: "등록자와 동일한 포인트가 신청자로부터 예치되며"
        // 잔액 부족 시 UserCommandService가 PointException(POINT_001) 던짐 → 트랜잭션 롤백
        //
        // TODO: User 도메인 머지 후 활성화
        // userCommandService.deductPoint(applicantId, post.getAuthorDeposit());
        //
        // 호출 시 User 담당자에게 요청할 사항:
        //   1) PointTransaction 기록 (type=DEPOSIT, description="매칭 신청 예치", matchId=null 또는 매칭 생성 후 ID)
        //   2) 같은 트랜잭션 내 처리 (이 메서드의 트랜잭션에 자동 참여)

        Match match = Match.builder()
                .postId(postId)
                .applicantId(applicantId)
                .applicantDeposit(post.getAuthorDeposit())
                .build();
        Match savedMatch = matchRepository.save(match);

        post.match();

        // TODO: Chat 도메인 머지 후 활성화
        // Long chatRoomId = chatRoomCommandService.createRoom(
        //         savedMatch.getId(),
        //         post.getAuthorId(),
        //         applicantId
        // );

        Long chatRoomId = null;

        // TODO: User 머지 후 실제 호출로 교체
        // String authorNickname = userQueryService.getUserById(post.getAuthorId()).getNickname();
        // String applicantNickname = userQueryService.getUserById(applicantId).getNickname();
        String authorNickname = "임시-등록자";
        String applicantNickname = "임시-신청자";

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
