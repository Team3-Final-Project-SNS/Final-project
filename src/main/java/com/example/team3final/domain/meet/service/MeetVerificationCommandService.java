package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;

// MeetVerification 도메인의 사용자 요청 기반 인증/연장 변경 작업을 담당하는 서비스
public interface MeetVerificationCommandService {

    // GPS 장소 인증
    PlaceVerificationResponseDto createPlaceVerification(
            Long userId,
            Long matchId,
            PlaceVerificationRequestDto requestDto
    );

    // QR 스캔
    QrScanResponseDto createQrScan(
            Long userId,
            Long matchId,
            QrScanRequestDto requestDto
    );

    // 만남 시간 연장 요청
    CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId);

    // 만남 시간 연장 수락
    AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId);

    // 만남 시간 연장 거절
    RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId);
}
