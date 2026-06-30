package com.example.team3final.domain.meet.service.support;

import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.ZoneOffset;
import java.util.List;

// Meet 인증 도메인에서 사용하는 정책 상수 모음
public class MeetVerificationPolicy {

    private MeetVerificationPolicy() {
    }

    // 한국 시간대 기준 적용
    public static final ZoneOffset KST = ZoneOffset.ofHours(9);
    // QR 토큰 TTL: 발급 시점 + 10분
    public static final long QR_TOKEN_VALIDITY_MINUTES = 10;
    // 장소 인증 가능 시작: 만남 시간 10분 전
    public static final long VERIFICATION_BEFORE_MINUTES = 10;
    // 장소 인증 가능 종료: 만남 시간 10분 후
    public static final long VERIFICATION_AFTER_MINUTES = 10;
    // 노쇼 예정 판정 기준: 만남 시간 + 10분
    public static final long NO_SHOW_JUDGE_MINUTES = 10;
    // 발표회 시연을 위해 QR 단계 fallback 진입 기준 임시 조정
    public static final long QR_FALLBACK_AFTER_MINUTES = 3;
    // 연장 요청 타임아웃: 요청 시각 + 5분
    public static final long EXTENSION_TIMEOUT_MINUTES = 5;
    // 연장 시간: 1회 10분
    public static final long EXTENSION_MINUTES = 10;
    // 노쇼 확정까지 이의제기 가능 시간: 24시간
    public static final long NO_SHOW_CONFIRM_HOURS = 24;
    // 위치 업데이트 5초 주기를 기준으로 둔 안전 여유 값
    public static final long LOCATION_FRESHNESS_SECONDS = 15;

    // GPS 장소 인증 정책 반경 50m에 위치 오차 허용 범위 10m를 더한 서버 판정 반경
    public static final double MEETING_RADIUS_METERS = 60.0;
    public static final double NO_SHOW_RADIUS_METERS = MEETING_RADIUS_METERS;
    public static final double PLACE_VERIFICATION_RADIUS_METERS = MEETING_RADIUS_METERS;

    // 노쇼 예정 상태 목록: 이의제기/확정 처리 대상
    public static final List<VerificationStatus> NO_SHOW_STATUSES = List.of(
            VerificationStatus.HOST_NO_SHOW,
            VerificationStatus.GUEST_NO_SHOW,
            VerificationStatus.BOTH_NO_SHOW
    );
}
