package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.entity.MeetVerification;

// MeetVerification 도메인의 타 도메인/스케줄러 호출용 내부 기능을 제공하는 서비스
public interface MeetVerificationInternalService {

    // 매칭 생성 시 MeetVerification 초기 레코드 생성
    void createPendingVerification(Long matchId);

    // 스케줄러 -> 5분 타임아웃 된 연장 요청 일괄 EXPIRED 처리
    void expireTimeoutExtensions();

    // 이의제기 상세 조회 - matchId로 단건 조회
    MeetVerification getByMatchId(Long matchId);
}
