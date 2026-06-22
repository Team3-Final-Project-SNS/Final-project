package com.example.team3final.domain.dispute.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DisputeType {

    FUNERAL_CEREMONY("(경)조사"),
    MEDICAL_EMERGENCY("응급실"),
    PHONE_MALFUNCTION("스마트폰 고장"),
    GPS_ERROR("GPS 인증 오류"),
    QR_ERROR("QR 코드 인식 오류"),
    ADMIN_OVERRIDE("관리자 권한으로 허용");

    private final String description;

    /**
     * 이 타입은 결과상 "매칭 취소" 방향인지 여부.
     * 관리자 상세조회 화면에서 안내 문구로 활용.
     */
    public boolean isMatchCancelType() {
        return this == FUNERAL_CEREMONY || this == MEDICAL_EMERGENCY;
    }

    /**
     * 이 타입은 결과상 "만남인증 완료" 처리 방향인지 여부.
     */
    public boolean isMeetCompletionType() {
        return this == PHONE_MALFUNCTION || this == GPS_ERROR || this == QR_ERROR;
    }
}
