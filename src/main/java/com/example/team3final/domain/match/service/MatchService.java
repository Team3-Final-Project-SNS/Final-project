package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.*;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MatchService {

    /**
     * 매칭 신청 / 생성 (선착순)
     *
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);

    /**
     * 매칭 완료 처리 — 도메인 간 호출용 (만남 인증 QR 성공 시)
     * 오케스트레이션: Match→COMPLETED, Post→COMPLETED, 채팅방 비활성화, 양측 환불
     *
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    void completeMatch(Long matchId);

    // 이의제기 접수 시 호출 — Match 상태를 DISPUTED로 변경
    // MeetVerification.markDispute()와 함께 호출되어 양측 상태를 동시에 이의제기 상태로 전환
    // 포인트 정산 없음 — 이의제기가 종결될 때까지 예치금 보류
    void markDisputed(Long matchId);

    // 이의제기 판정: 만남 완료 인정
    // GPS/QR 오류 등으로 인증만 실패했고 실제 만남은 있었다고 관리자가 판정한 경우
    // 포인트 환불은 호출자(AdminDisputeService.judgeDispute ACCEPTED 분기)에서 처리됨
    // 여기서는 Match→COMPLETED, Post→COMPLETED, 후기 알림 예약만 처리
    void completeMatchByDispute(Long matchId);

    // 이의제기 판정: 매칭 취소 인정
    // 장례식/응급실 등 불가피한 사유로 노쇼가 아닌 취소로 인정된 경우
    // 포인트 환불은 호출자(AdminDisputeService.applyDisputeJudgment ACCEPTED 분기)에서 처리됨
    // 여기서는 Match→CANCELLED, Post→CANCELLED 상태 전이만 처리
    void cancelMatchByDispute(Long matchId);

    // 등록자 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→AUTHOR_NO_SHOW, Post→COMPLETED, 등록자 예치금 몰수, 신청자 전액 환급
    void markAuthorNoShow(Long matchId);

    // 신청자 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→APPLICANT_NO_SHOW, Post→COMPLETED, 신청자 예치금 몰수, 등록자 전액 환급
    void markApplicantNoShow(Long matchId);

    // 양측 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→BOTH_NO_SHOW, Post→COMPLETED, 양측 예치금 모두 몰수
    void markBothNoShow(Long matchId);

    // 이의제기 부분 수용(PARTIALLY_ACCEPTED) 판정 시 호출
    // 이의제기자에게 50% 환불은 judgeDispute()에서 이미 처리됨
    // 여기서는 포인트 정산 없이 restoredStatus 기반으로 Match 노쇼 상태만 확정
    // restoredStatus: DISPUTE 진입 전 백업해둔 원래 노쇼 예정 상태 (HOST/GUEST/BOTH_NO_SHOW)
    void markNoShowByDisputeWithoutPointSettlement(Long matchId, VerificationStatus restoredStatus);

    /**
     * 매칭 취소 — Controller 직접 호출 (명세서 5.3)
     *
     * @param matchId 취소할 매칭 ID
     * @param userId  취소 요청자 ID (당사자 검증 + 50%/100% 구분)
     * @param request 취소 요청 DTO
     * @throws MatchException MATCH_001/002/006/007
     */
    CancelMatchResponseDto cancelMatch(Long matchId, Long userId, CancelMatchRequestDto request);


    /**
     * 매칭 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     *
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    Match getMatchById(Long matchId);

    /**
     * 매칭 정보 조회 — 도메인 간 호출용 (DTO 반환)
     *
     * @throws MatchException MATCH_001
     */
    MatchInfoDto getMatchInfo(Long matchId);

    /**
     * 매칭 상세 조회 — Controller 직접 호출 (명세서 5.2)
     *
     * @throws MatchException MATCH_001 — 매칭 없음 / MATCH_002 — 당사자 아님
     */
    GetMatchResponseDto getMatch(Long matchId, Long currentUserId);

    /**
     * 특정 사용자가 특정 게시글에 이미 신청했는지 확인합니다.
     *
     * AI 매칭, 게시글 검증 등 다른 도메인에서 중복 신청 여부만 필요할 때
     * MatchRepository를 직접 참조하지 않고 Match 도메인 서비스로 조회합니다.
     */
    boolean hasAppliedToPost(Long postId, Long applicantId);

    /**
     * 특정 게시글에 생성된 매칭 ID 목록을 조회합니다.
     *
     * Review 도메인이 단체 만남의 리뷰 평균을 계산할 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Long> getMatchIdsByPostId(Long postId);

    /**
     * 특정 게시글에 속한 COMPLETED 매칭 목록을 조회합니다.
     *
     * Chat 도메인이 만남 완료 후 채팅방 READ_ONLY 전환 알림을 보낼 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Match> getCompletedMatchesByPostId(Long postId);

    /**
     * COMPLETED 상태의 매칭을 Optional로 조회합니다.
     *
     * Review 도메인이 후기 작성 마지막 날 알림 처리 시
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    Optional<Match> findCompletedMatchById(Long matchId);


    /**
     * 내 매칭 목록 조회 — Controller 직접 호출 (명세서 5.4)
     *
     * @param status null이면 전체 조회
     * @param pageable 페이징 + 정렬 (Controller에서 createdAt DESC로 생성)
     */
    PageResponseDto<GetMatchesResponseDto> getMatches(Long userId, MatchStatus status, Pageable pageable);

    /**
     * 매칭 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 verification에 대해
     *         Match 정보를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     *
     * 반환 형태:
     *  - Key   = matchId
     *  - Value = MatchInfoDto
     *  - 호출 측에서 O(1) 룩업이 가능하도록 Map으로 반환
     *
     * Contract:
     *  - matchIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 matchId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     *    (단건 getMatchInfo와의 의도적인 차이 — 부분 실패가 전체 실패로 번지지 않도록)
     */
    Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds);
}
