package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;

public interface MeetVerificationService {

    // GPS 장소 인증
    PlaceVerificationResponseDto createPlaceVerification(
            Long matchId,
            Long userId,
            PlaceVerificationRequestDto requestDto
    );

    // Qr 토큰 발급/조회
    QrResponseDto getMeetQr(Long matchId, Long userId);


}
