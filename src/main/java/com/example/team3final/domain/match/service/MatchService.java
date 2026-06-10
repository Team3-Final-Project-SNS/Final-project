package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.MatchException;
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
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);

    // 이의제기 접수 시 호출 — Match 상태를 DISPUTED로 변경
    // MeetVerification.markDispute()와 함께 호출되어 양측 상태를 동시에 이의제기 상태로 전환
    // 포인트 정산 없음 — 이의제기가 종결될 때까지 예치금 보류
    void markDisputed(Long matchId);

    /**
     * 시스템 취소 — QR 만료 시점까지 양측 모두 현장에 있었으나 QR 인증 미완료
     * 귀책 없음 → 양측 예치금 전액 환불, 노쇼 아님
     * 사용처: MeetVerificationServiceImpl.judgeQrNoShow()
     */
    void cancelMatchBySystem(Long matchId);

    // 관리자 ACCEPTED 판정
    // 노쇼가 아니라고 인정된 케이스이므로 Match를 정상 완료 처리
    // 포인트 정산은 Match 도메인에서 처리
    // 반환값: 이의제기자에게 실제 반환된 포인트
    int completeSingleMatchByDispute(Long matchId, Long submitterId);

    // 관리자 PARTIALLY_ACCEPTED 판정
    // 노쇼는 맞지만 이의제기자의 사유가 일부 인정된 케이스
    // 포인트 정산은 Match 도메인에서 처리
    // 반환값: 이의제기자에게 실제 반환된 포인트
    int markNoShowByDispute(
            Long matchId,
            VerificationStatus restoredStatus,
            Long submitterId
    );


    // 등록자 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→AUTHOR_NO_SHOW, Post→COMPLETED, 등록자 예치금 몰수, 신청자 전액 환급
    void markAuthorNoShow(Long matchId);

    // 신청자 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→APPLICANT_NO_SHOW, Post→COMPLETED, 신청자 예치금 몰수, 등록자 전액 환급
    void markApplicantNoShow(Long matchId);

    // 양측 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→BOTH_NO_SHOW, Post→COMPLETED, 양측 예치금 모두 몰수
    void markBothNoShow(Long matchId);

    /**
     * 매칭 취소 — Controller 직접 호출 (명세서 5.3)
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
     * AI 매칭, 게시글 검증 등 다른 도메인에서 중복 신청 여부만 필요할 때
     * MatchRepository를 직접 참조하지 않고 Match 도메인 서비스로 조회합니다.
     */
    boolean hasAppliedToPost(Long postId, Long applicantId);

    /**
     * 특정 게시글에 생성된 매칭 ID 목록을 조회합니다.
     * Review 도메인이 단체 만남의 리뷰 평균을 계산할 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Long> getMatchIdsByPostId(Long postId);

    /**
     * 특정 게시글에 속한 COMPLETED 매칭 목록을 조회합니다.
     * Chat 도메인이 만남 완료 후 채팅방 READ_ONLY 전환 알림을 보낼 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Match> getCompletedMatchesByPostId(Long postId);

    /**
     * COMPLETED 상태의 매칭을 Optional로 조회합니다.
     * Review 도메인이 후기 작성 마지막 날 알림 처리 시
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    Optional<Match> findCompletedMatchById(Long matchId);


    /**
     * 내 매칭 목록 조회 — Controller 직접 호출 (명세서 5.4)
     * @param status null이면 전체 조회
     * @param pageable 페이징 + 정렬 (Controller에서 createdAt DESC로 생성)
     */
    PageResponseDto<GetMatchesResponseDto> getMatches(Long userId, MatchStatus status, Pageable pageable);

    /**
     * 매칭 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 verification에 대해
     *         Match 정보를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     * 반환 형태:
     *  - Key   = matchId
     *  - Value = MatchInfoDto
     *  - 호출 측에서 O(1) 룩업이 가능하도록 Map으로 반환
     * Contract:
     *  - matchIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 matchId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     *    (단건 getMatchInfo와의 의도적인 차이 — 부분 실패가 전체 실패로 번지지 않도록)
     */
    Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds);

    // QR 스캔 성공 시 Match 단건만 COMPLETE 처리
    // 해당 Match 1개만 COMPLETE 전환
    boolean completeSingleMatch(Long matchId);

    // 모든 활성 매칭이 종료된 뒤 Post 전체 종결 처리
    // 등록자 책임비 환급, 중복 호출되어도 한 번만 처리되도록 멱등성 보장
    void completePostIfAllMatchesCompleted(Long postId);
}
