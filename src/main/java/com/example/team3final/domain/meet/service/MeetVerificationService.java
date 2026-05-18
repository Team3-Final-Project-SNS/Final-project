package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.dto.response.QrScanResponseDto;

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


}
