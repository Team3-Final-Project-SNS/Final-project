package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostCommandService;
import com.example.team3final.domain.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchCommandServiceImpl implements MatchCommandService{

    private final MatchRepository matchRepository;
    private final MatchQueryService matchQueryService;
    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;
    private final ChatService chatService;

    // TODO: User 도메인 머지 후 활성화
    // private final UserCommandService userCommandService;   // 포인트 차감
    // private final UserQueryService userQueryService;       // 닉네임 조회
    //

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

         chatService.createChatRoom(
                 savedMatch.getId(),
                 post.getAuthorId(),
                 applicantId
         );

        Long chatRoomId = chatService.createChatRoom(savedMatch.getId(), post.getAuthorId(), applicantId);

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

    /**
     * 매칭 완료 처리 (오케스트레이션)
     *
     * 호출 시점: 만남 인증(정)이 QR 스캔 성공 처리할 때
     * 처리 순서:
     *   1) Match 조회 + 도메인 메서드 호출 (status=COMPLETED, completedAt=now)
     *   2) Post 상태 → COMPLETED
     *   3) 채팅방 비활성화 (isActive=false)
     *   4) [TODO] 포인트 환불
     */
    @Override
    public void completeMatch(Long matchId) {
        // ===== ① Match 조회 =====
        // 같은 도메인의 QueryService에 위임 — NotFound 처리 일원화
        Match match = matchQueryService.getMatchById(matchId);

        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_INVALID_STATUS);
        }

        // ===== ② Match 상태 변경: → COMPLETED =====
        // 엔티티의 complete() 메서드가 status + completedAt 둘 다 책임짐
        // 우리는 호출만 하면 됨 — 상태 전이 규칙이 엔티티 안에 캡슐화돼 있어서
        match.complete();

        // ===== ③ Post 상태 변경: → COMPLETED =====
        // Post 도메인의 Service에 위임 — Post 엔티티를 직접 가져오지 않고 도메인 경계 유지
        // match.getPostId()로 PostId 추출 후 Post 도메인이 자기 일을 함
        postCommandService.completePost(match.getPostId());

        // 포인트 환불 (TODO)
        // 만남 정상 완료 시: 양측 모두 100% 환불
        // - 등록자: post.authorDeposit 100% 환불 (type=REFUND)
        // - 신청자: match.applicantDeposit 100% 환불 (type=REFUND)
        //
        // TODO: User 도메인 머지 후 활성화
        // Post post = postQueryService.getPostById(match.getPostId());
        // userCommandService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), RefundType.REFUND);
        // userCommandService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), RefundType.REFUND);
    }

    @Override
    public void markAuthorNoShow(Long matchId) {
        // Match 조회 (없으면 MATCH_001 예외)
        Match match = matchQueryService.getMatchById(matchId);

        // MATCHED 상태가 아니면 스킵
        // 스케줄러는 중단되면 안 되므로 예외 대신 return
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        // Match 상태 → AUTHOR_NO_SHOW (등록자 노쇼)
        match.markNoShow(MatchStatus.AUTHOR_NO_SHOW);

        // Post 상태 → COMPLETED (노쇼도 게시글은 종료)
        postCommandService.completePost(match.getPostId());

        // TODO: 피해자(신청자) 포인트 전액 환불, 노쇼(등록자) 포인트 몰수 (User 도메인 머지 후)
    }

    @Override
    public void markApplicantNoShow(Long matchId) {
        // Match 조회 (없으면 MATCH_001 예외)
        Match match = matchQueryService.getMatchById(matchId);

        // MATCHED 상태가 아니면 스킵
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        // Match 상태 → APPLICANT_NO_SHOW (신청자 노쇼)
        match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);

        // Post 상태 → COMPLETED (노쇼도 게시글은 종료)
        postCommandService.completePost(match.getPostId());

        // TODO: 피해자(등록자) 포인트 전액 환불, 노쇼(신청자) 포인트 몰수 (User 도메인 머지 후)
    }

    @Override
    public void markBothNoShow(Long matchId) {
        // Match 조회 (없으면 MATCH_001 예외)
        Match match = matchQueryService.getMatchById(matchId);

        // MATCHED 상태가 아니면 스킵
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        // Match 상태 → BOTH_NO_SHOW (양측 노쇼)
        match.markNoShow(MatchStatus.BOTH_NO_SHOW);

        // Post 상태 → COMPLETED (노쇼도 게시글은 종료)
        postCommandService.completePost(match.getPostId());

        // TODO: 양측 모두 포인트 몰수 (User 도메인 머지 후)
    }


}
