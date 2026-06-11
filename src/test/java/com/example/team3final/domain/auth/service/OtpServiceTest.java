package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.OtpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @InjectMocks
    private OtpServiceImpl otpService;

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private OtpProperties otpProperties;

    @Test
    @DisplayName("OTP 발송 - 성공")
    void sendOtp_Success() {
        // when
        otpService.sendOtp("test@univ.ac.kr", "123456");

        // then
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
