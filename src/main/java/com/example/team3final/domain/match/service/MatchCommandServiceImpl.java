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
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
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
    private final UserPointService userPointService;
    private final UserService userService;


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
        // 등록자는 게시글 작성 시점에 이미 차감 완료 — 여기선 신청자만 처리
        // 잔액 부족 시 UserPointService가 예외 던짐 → 트랜잭션 전체 롤백
        // matchId는 아직 생성 전이라 null — ERD상 point_transaction.match_id NULL 허용
        userPointService.deductPoint(applicantId, post.getAuthorDeposit(), null);

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

        Long chatRoomId = chatService.createChatRoom(
                savedMatch.getId(),
                post.getAuthorId(),
                applicantId
        );

        // UserService.getUserInfo()로 닉네임 조회 — UserInfoDto에 nickname 포함
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

    /**
     * 매칭 완료 처리 (오케스트레이션)
     *
     * 호출 시점: 만남 인증(정)이 QR 스캔 성공 처리할 때
     * 처리 순서:
     *   1) Match 조회 + 도메인 메서드 호출 (status=COMPLETED, completedAt=now)
     *   2) Post 상태 → COMPLETED
     *   3) 채팅방 비활성화 (isActive=false)
     *   4)  포인트 환불
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

        // 포인트 환불
        // 만남 정상 완료 시: 양측 모두 100% 환불
        // - 등록자: post.authorDeposit 100% 환불 (type=REFUND)
        // - 신청자: match.applicantDeposit 100% 환불 (type=REFUND)
        // ===== ④ 포인트 환불: 양측 100% 전액 반환 =====
        // Post 엔티티에서 등록자 ID, 예치금 조회 (PostQueryService 경유 — repository 직접 접근 아님)
        Post post = postQueryService.getPostById(match.getPostId());

        // 등록자 전액 환급 (REFUND) — post.authorDeposit이 등록자 예치금
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);

        // 신청자 전액 환급 (REFUND) — match.applicantDeposit이 신청자 예치금
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);

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

        // ===== 포인트 처리 =====
        // Post 엔티티에서 등록자 ID, 예치금 조회
        Post post = postQueryService.getPostById(match.getPostId());

        // 등록자(노쇼 당사자): 예치금 몰수
        // 예치 시점에 이미 잔액 차감 완료 → user.point 변경 없이 PENALTY 거래 기록만 남김
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);

        // 신청자(피해자): 예치금 전액 환급
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
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

        // ===== 포인트 처리 =====
        Post post = postQueryService.getPostById(match.getPostId());

        // 등록자(피해자): 예치금 전액 환급
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);

        // 신청자(노쇼 당사자): 예치금 몰수
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
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

        // ===== 포인트 처리: 양측 모두 몰수 =====
        Post post = postQueryService.getPostById(match.getPostId());

        // 등록자 몰수
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);

        // 신청자 몰수
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }


}
