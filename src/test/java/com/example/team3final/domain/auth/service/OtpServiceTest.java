package com.example.team3final.domain.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OTP 서비스 단위 테스트")
class OtpServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private OtpServiceImpl otpService;

    @Test
    @DisplayName("OTP 발송은 수신자와 인증번호를 포함한 메일을 전송한다")
    void sendOtp_shouldSendMailMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        otpService.sendOtp("user@test.ac.kr", "123456");

        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("user@test.ac.kr");
        assertThat(message.getText()).contains("123456");
    }
}
