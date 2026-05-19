package com.example.team3final.domain.auth.util;

// Redis Key를 한 곳에서 관리하는 클래스
public class OtpRedisKeyUtil {

    // OTP 코드 저장 키
    public static String otpCodeKey(String email) {
        return "otp:code:" + email;
    }

    // 1분 내 재발송 방지키
    public static String cooldownKey(String email) {
        return "otp:cooldown:" + email;
    }

    // 1시간 내 최대 3회 제한키
    public static String resendCountKey(String email) {
        return "otp:resend:count:" + email;
    }

}
