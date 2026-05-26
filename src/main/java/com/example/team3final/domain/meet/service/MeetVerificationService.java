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
            Long matchId,
            Long userId,
            PlaceVerificationRequestDto requestDto
    );

    // QR 토큰 발급/조회
    QrResponseDto getMeetQr(Long matchId, Long userId);

    // QR 스캔
    QrScanResponseDto createQrScan(Long matchId, Long userId, QrScanRequestDto requestDto);

    // 인증 상태 조회
    MeetVerificationResponseDto getMeetVerification(Long matchId, Long userId);

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

    // 연장 요청
    CreateMeetExtensionResponseDto createMeetExtension(Long matchId, Long userId);

    // 연장 수락
    AcceptMeetExtensionResponseDto acceptMeetExtension(Long matchId, Long userId);

    // 연장 거절
    RejectMeetExtensionResponseDto rejectMeetExtension(Long matchId, Long userId);

    // 연장 상태 조회
    GetMeetExtensionResponseDto getMeetExtension(Long matchId, Long userId);

    // 스케줄러 -> 5분 타임아웃 된 연장 요청 일괄 EXPIRED 처리
    void expireTimeoutExtensions();
}
