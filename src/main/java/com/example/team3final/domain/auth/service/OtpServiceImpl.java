package com.example.team3final.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final JavaMailSender mailSender;

    // 인증번호 이메일 생성
    @Override
    @Async
    public void sendOtp(String to, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("dalsun_rin@naver.com");
        message.setTo(to);
        message.setSubject("[한끼팟] 이메일 인증번호 안내");
        message.setText(buildOtpMessage(otpCode));
        mailSender.send(message);
    }

    private String buildOtpMessage(String otpCode) {
        return String.format(
                "안녕하세요 한끼팟 입니다.\n\n" +
                "인증번호 : %s\n\n" +
                "5분안에 인증을 완료해주세요",
                otpCode
        );
    }
}
