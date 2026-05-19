package com.example.team3final.domain.auth.util;

import java.security.SecureRandom;

public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // 6자리 숫자 OTP 생성 (000000 ~ 999999)
    public static String generator() {
        int otp = RANDOM.nextInt(1_000_000); // 0이상 999999 이하
        return String.format("%06d", otp); // %06d : 6자리 미만이면 앞에 0을 채워 6자리로 생성
    }
}
