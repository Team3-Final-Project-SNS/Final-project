package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.entity.MeetVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// MeetVerification 도메인의 노쇼 판정 및 노쇼 확정 처리를 담당하는 서비스
public interface MeetVerificationNoShowService {

    // 노쇼 자동 판정
    // GPS 장소 인증 단계 노쇼
    void judgeGpsNoShow();

    // QR 만남 인증 단계 노쇼
    void judgeQrNoShow();

    // 노쇼 확정 배치 — _NO_SHOW 상태가 된 지 24시간 지난 건 알림 발송 + 확정 처리
    void judgeNoShowConfirmed();

    // 특정 Post에 속한 노쇼/이의제기 상태 MeetVerification들을 관리자 노쇼 확정 상태로 일괄 정리
    // 등록자 노쇼처럼 Post 전체 책임이 확정되는 경우 사용
    void confirmNoShowByPost(Long postId);

    // Admin 도메인에서 사용할 노쇼 후보군 조회
    // HOST_NO_SHOW, GUEST_NO_SHOW, BOTH_NO_SHOW
    Page<MeetVerification> getNoShowCandidates(Pageable pageable);
}
