package com.example.team3final.domain.meet.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("만남 인증 정책 단위 테스트")
class MeetVerificationPolicyTest {

    @Test
    @DisplayName("GPS 관련 서버 판정 반경은 위치 오차를 포함해 60m이다")
    void gpsRadius_shouldBeSixtyMeters() {
        assertThat(MeetVerificationPolicy.MEETING_RADIUS_METERS).isEqualTo(60.0);
        assertThat(MeetVerificationPolicy.NO_SHOW_RADIUS_METERS).isEqualTo(60.0);
        assertThat(MeetVerificationPolicy.PLACE_VERIFICATION_RADIUS_METERS).isEqualTo(60.0);
    }
}
