package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.VerificationException;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.dto.response.QrScanResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetVerificationServiceImpl implements MeetVerificationService {

    // GPS검증, 상태 전환, 역할 구분 서비스
    private final MeetVerificationRepository meetVerificationRepository;

    // GPS 오차범위까지 고려한 인증 반경
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;
    // 지구 반지름
    private static final int EARTH_RADIUS_METERS = 6371000;

    // QR 토큰 TTL - 장소 인증 완료 시점 + 30분
    private static final long QR_TOKEN_VALIDITY_MINUTES = 30;

    // GPS 장소 인증
    @Override
    @Transactional
    public PlaceVerificationResponseDto createPlaceVerification(
            Long matchId,
            Long userId,
            PlaceVerificationRequestDto requestDto) {

        // matchId로 MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                //TODO: match 에러 코드 생성되면 적용
                .orElseThrow( () -> new IllegalArgumentException("Meet Verification Not Found"));

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

    @Override
    public QrResponseDto getMeetQr(Long matchId, Long userId) {
        // matchId 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                //TODO: match 에러코드 생성되면 적용
                .orElseThrow( () -> new IllegalArgumentException("Meet Verification Not Found"));

        // 등록자인지 확인
        // TODO: Match 연결 후 userId == authorId 비교로 확인 해야함

        // 장소 인증 완료된 상태인지 체크
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new VerificationException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // QR 토큰 이미 발급했는지 확인
        if (meetVerification.getQrToken() != null) {
            // 이미 만료됐으면 예외 던지기
            if (meetVerification.isQrExpired()) {
                throw new VerificationException(ErrorCode.QR_EXPIRED);
            }

            return QrResponseDto.from(matchId, meetVerification);
        }

        // QR 토큰 신규 발급
        // "hp_qr_" -> API 명세서 상의 접두사, qr 토큰 식별 용도
        String qrToken = "hp_qr_" + UUID.randomUUID().toString().replace("-", "");

        // 양측 장소 인증 완료 시점 + 30분
        // 둘 중 누가 먼저 올지 모르기 때문에, 둘 다 null이 아님이 보장된 상태일 때
        LocalDateTime lastVerifiedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt() : meetVerification.getApplicantPlaceVerifiedAt();

        LocalDateTime expiresAt = lastVerifiedAt.plusMinutes(QR_TOKEN_VALIDITY_MINUTES);

        // 엔티티에 QR 토큰 저장
        meetVerification.issueQrToken(qrToken, expiresAt);

        return QrResponseDto.from(matchId, meetVerification);
    }

    @Override
    public QrScanResponseDto createQrScan(Long matchId, Long userId, QrScanRequestDto requestDto) {
        // matchId 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                // TODO: Match 에러코드 적용해야 함
                .orElseThrow( () -> new IllegalArgumentException("Meet Verification Not Found"));

        // 신청자인지 확인
        // TODO: Match 연결 후 userId == applicantId 비교

        // 장소 인증
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new VerificationException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // QR 토큰 만료 여부 체크
        if (meetVerification.isQrExpired()) {
            throw new VerificationException(ErrorCode.QR_EXPIRED);
        }

        // QR 토큰 일치 여부 검증
        if (!requestDto.getQrToken().equals(meetVerification.getQrToken())) {
            throw new VerificationException(ErrorCode.SCAN_INVALID_QR_TOKEN);
        }

        // 만남 인증 완료 처리
        meetVerification.meetVerifiedDone();

        // TODO: Match 상태 COMPLETED로 변경

        // TODO: 양측 예치 포인트 전액 환급 필요

        return QrScanResponseDto.from(matchId, meetVerification, MatchStatus.COMPLETED, 0);
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
