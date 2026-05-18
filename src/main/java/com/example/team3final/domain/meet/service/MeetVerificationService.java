package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.VerificationException;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetVerificationService {

    // GPS검증, 상태 전환, 역할 구분 서비스
    private final MeetVerificationQueryService meetVerificationQueryService;

    // GPS 오차범위까지 고려한 인증 반경
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;
    // 지구 반지름
    private static final int EARTH_RADIUS_METERS = 6371000;

    // GPS 장소 인증
    public PlaceVerificationResponseDto createPlaceVerification(
            Long matchId,
            Long userId,
            PlaceVerificationRequestDto requestDto) {

        // matchId로 MeetVerification 조회
        MeetVerification meetVerification = meetVerificationQueryService.getByMatchId(matchId);

        // 이미 인증 완료된건지 체크
        // TODO: Match 연결 후 userId 기반으로 등록자/신청자 각각 체크로 교체 필요
        validateNotAlreadyVerified(meetVerification);

        // TODO: Match -> Post의 placeLat, placeLng 조회로 교체 필요
        BigDecimal placeLat = new BigDecimal("37.566500");
        BigDecimal placeLng = new BigDecimal("122.43200");

        // BigDecimal → double 변환: Math 삼각함수가 double만 지원하므로 계산 직전에만 변환
        double distanceMeters = calculateDistance(
                requestDto.getCurrentLat().doubleValue(), requestDto.getCurrentLng().doubleValue(),
                placeLat.doubleValue(), placeLng.doubleValue()
        );

        // 반경 60m(오차 범위 포함) 벗어났는지 체크
        if (distanceMeters > PLACE_VERIFICATION_RADIUS_METERS) {
            throw new VerificationException(ErrorCode.GPS_OUT_OF_RANGE);
        }

        if (!meetVerification.isAuthorPlaceVerified()) {
            meetVerification.verifyAuthorPlace();
        } else {
            meetVerification.verifyApplicantPlace();
        }

        boolean bothVerified = meetVerification.getStatus() == VerificationStatus.VERIFIED;

        return PlaceVerificationResponseDto.from(meetVerification, distanceMeters, bothVerified);
    }

    // 이미 인증 완료된건지 검증하는 로직
    private void validateNotAlreadyVerified(MeetVerification meetVerification) {
        if (meetVerification.getStatus() == VerificationStatus.VERIFIED) {
            throw new VerificationException(ErrorCode.GPS_ALREADY_VERIFIED);
        }
    }

    // Haversine 공식으로 두 GPS 좌표 사이 거리 계산
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double n = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double m = 2 * Math.atan2(Math.sqrt(n), Math.sqrt(1 - n));

        return EARTH_RADIUS_METERS * m;
    }

}
