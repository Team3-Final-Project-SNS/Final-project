package com.example.team3final.domain.meet.service.support;

import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.ZoneOffset;
import java.util.List;

// meet 인증 도메인에서 사용하는 상수값들을 모아둔 클래스
public class MeetVerificationPolicy {

    private MeetVerificationPolicy() {
    }

    // 한국 시간대 오프셋 — Unix Timestamp 변환 시 KST(UTC+9) 기준 적용
    public static final ZoneOffset KST = ZoneOffset.ofHours(9);
    // QR 토큰 TTL - 상호 장소 인증 완료 시점 + 30분
    public static final long QR_TOKEN_VALIDITY_MINUTES = 30;
    // 장소 인증 가능 시간 : 만남 시간 10분 전 ~ 30분간
    public static final long VERIFICATION_BEFORE_MINUTES = 10;
    // 장소 인증 활성 시간 (만남 약속 시각 기준 20분)
    public static final long VERIFICATION_AFTER_MINUTES = 20;
    // 노쇼 판정 기준 : 장소 인증 종료 시각 기준 (meetAt - 10분 시작 + 30분 = meetAt + 20분)
    public static final long NO_SHOW_JUDGE_MINUTES = 20;
    // 연장 요청 타임아웃 : 요청 시각 + 5분
    public static final long EXTENSION_TIMEOUT_MINUTES = 5;
    // 연장 시간 : 1회 10분
    public static final long EXTENSION_MINUTES = 10;

    //    노쇼 확정까지 이의제기 가능 시간: 24시간
    //    private static final long NO_SHOW_CONFIRM_HOURS = 24;
    // ===== 변경 (테스트용 — 배포 전 24시간으로 원복 필요) =====
    public static final long NO_SHOW_CONFIRM_MINUTES = 10;
    // 5초마다 위치 업데이트 정책을 기준으로 안전 여유 값 15초
    public static final long LOCATION_FRESHNESS_SECONDS = 15;
    // GPS 오차범위 고려한 노쇼 범위 (정책 50m + 오차 10m)
    public static final double NO_SHOW_RADIUS_METERS = 60.0;
    // 장소 인증 허용 반경
    // 사용자에게 안내되는 약속 장소 반경은 50m.
    // 다만 GPS 오차를 고려해서 서버 검증은 10m 여유를 둔 60m까지 허용.
    public static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;

    // 노쇼 예정 상태 목록 — 이의제기/확정 처리 대상
    public static final List<VerificationStatus> NO_SHOW_STATUSES = List.of(
            VerificationStatus.HOST_NO_SHOW,
            VerificationStatus.GUEST_NO_SHOW,
            VerificationStatus.BOTH_NO_SHOW
    );
}
