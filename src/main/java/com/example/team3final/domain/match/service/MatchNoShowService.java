package com.example.team3final.domain.match.service;

import com.example.team3final.domain.match.context.NoShowDecision;
import com.example.team3final.domain.match.context.NoShowSettlementResult;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.util.List;

// Match 도메인의 노쇼 및 이의제기 결과 반영을 담당하는 서비스
public interface MatchNoShowService {

    // 이의제기 접수 시 호출 — Match 상태를 DISPUTED로 변경
    // MeetVerification.markDispute()와 함께 호출되어 양측 상태를 동시에 이의제기 상태로 전환
    // 포인트 정산 없음 — 이의제기가 종결될 때까지 예치금 보류
    void markDisputed(Long matchId);

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
    NoShowSettlementResult markAuthorNoShow(Long matchId);

    // 신청자 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→APPLICANT_NO_SHOW, Post→COMPLETED, 신청자 예치금 몰수, 등록자 전액 환급
    NoShowSettlementResult markApplicantNoShow(Long matchId);

    // 양측 노쇼 확정 — 배치(judgeNoShowConfirmed) 또는 이의제기 REJECTED 판정 시 호출
    // 처리: Match→BOTH_NO_SHOW, Post→COMPLETED, 양측 예치금 모두 몰수
    NoShowSettlementResult markBothNoShow(Long matchId);

    // 배치에서 같은 Post의 판정 결과를 한 트랜잭션으로 정산한다.
    NoShowSettlementResult finalizeNoShows(Long postId, List<NoShowDecision> decisions);
}
