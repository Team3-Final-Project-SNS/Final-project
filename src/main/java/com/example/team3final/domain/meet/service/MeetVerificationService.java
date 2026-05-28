package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.entity.MeetVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MeetVerificationService {

    // GPS 장소 인증
    PlaceVerificationResponseDto createPlaceVerification(
            Long userId,
            Long matchId,
            PlaceVerificationRequestDto requestDto
    );

    // QR 토큰 발급/조회
    QrResponseDto getMeetQr(Long userId, Long matchId);

    // QR 스캔
    QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto);

    // 인증 상태 조회
    MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId);

    // 매칭 생성 시 MeetVerification 초기 레코드 생성
    void createPendingVerification(Long matchId);

    // 노쇼 자동 판정
    // GPS 장소 인증 단계 노쇼
    void judgeGpsNoShow();

    // QR 만남 인증 단계 노쇼
    void judgeQrNoShow();

    // Admin 도메인에서 사용할 노쇼 후보군 조회
    // HOST_NO_SHOW, GUEST_NO_SHOW, BOTH_NO_SHOW
    Page<MeetVerification> getNoShowCandidates(Pageable pageable);

    // 만남 시간 연장 요청
    CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId);

    // 만남 시간 연장 수락
    AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId);

    // 만남 시간 연장 거절
    RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId);

    // 만남 시간 연장 상태 조회
    GetMeetExtensionResponseDto getMeetExtension(Long userId, Long matchId);

    // 스케줄러 -> 5분 타임아웃 된 연장 요청 일괄 EXPIRED 처리
    void expireTimeoutExtensions();

    // 이의제기 상세 조회 - matchId로 단건 조회
    MeetVerification getByMatchId(Long matchId);
}
