package com.example.team3final.domain.meet.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum VerificationStatus {

    PENDING,        // 초기 상태 — 양측 GPS 인증 전
    VERIFIED,       // 장소 인증 완료 — 양측 모두 반경 50m 진입, QR 단계 활성화
    DONE,           // 만남 인증 최종 완료 — QR 스캔 성공
    HOST_NO_SHOW,   // 등록자 노쇼 판정
    GUEST_NO_SHOW,  // 신청자 노쇼 판정
    BOTH_NO_SHOW,   // 양측 모두 노쇼 판정
    DISPUTE         // 이의제기 접수 중

}
