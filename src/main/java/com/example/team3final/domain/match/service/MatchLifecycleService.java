package com.example.team3final.domain.match.service;

// Match 도메인의 생명주기 전환을 담당하는 서비스
// QR 인증 완료, 시스템 취소, Post 완료 처리처럼 매칭 진행 상태와 게시글 완료 상태가 함께 전환되는 흐름을 처리
public interface MatchLifecycleService {

    /**
     * 시스템 취소 — QR 만료 시점까지 양측 모두 현장에 있었으나 QR 인증 미완료
     * 귀책 없음 → 양측 예치금 전액 환불, 노쇼 아님
     * 사용처: MeetVerificationServiceImpl.judgeQrNoShow()
     */
    void cancelMatchBySystem(Long matchId);

    // QR 스캔 성공 시 Match 단건만 COMPLETE 처리
    // 해당 Match 1개만 COMPLETE 전환
    boolean completeSingleMatch(Long matchId);

    // 모든 활성 매칭이 종료된 뒤 Post 전체 종결 처리
    // 등록자 책임비 환급, 중복 호출되어도 한 번만 처리되도록 멱등성 보장
    void completePostIfAllMatchesCompleted(Long postId);

    // QR 단계 진입 시점에 정원 미달 OPEN 게시글을 MATCHED로 확정
    void confirmPostMatchedForQrStage(Long postId);
}
