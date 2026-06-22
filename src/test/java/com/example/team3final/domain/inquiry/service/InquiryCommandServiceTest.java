package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.event.InquiryCreatedEvent;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InquiryCommandService 단위 테스트")
class InquiryCommandServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private AdminService adminService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private InquiryCommandServiceImpl inquiryCommandService;

    @Test
    @DisplayName("문의 생성은 사용자 검증, 제한 검증, 저장, 관리자 알림을 수행한다")
    void createInquiry_shouldSaveInquiryAndNotifyAdmins() {
        User user = activeUser();
        Inquiry savedInquiry = inquiry(1L, 10L, InquiryAnswerStatus.PENDING);
        CreateInquiryRequestDto request = CreateInquiryRequestDto.builder()
                .title("결제 문의")
                .content("결제 내역 확인이 필요합니다.")
                .type(InquiryType.PAYMENT)
                .build();
        when(userInternalService.findUserById(1L)).thenReturn(user);
        when(stringRedisTemplate.hasKey("inquiry:cooldown:1")).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("inquiry:daily:1")).thenReturn("0");
        when(inquiryRepository.save(any(Inquiry.class))).thenReturn(savedInquiry);
        when(adminService.getActiveAdminIds()).thenReturn(List.of(100L, 101L));

        CreateInquiryResponseDto result = inquiryCommandService.createInquiry(1L, request);

        assertThat(result.inquiryId()).isEqualTo(10L);
        verify(valueOperations).set(eq("inquiry:cooldown:1"), eq("1"), eq(Duration.ofMinutes(1)));
        verify(applicationEventPublisher).publishEvent(any(InquiryCreatedEvent.class));
        verify(notificationPublisher).sendInquirySubmitted(100L, 10L);
        verify(notificationPublisher).sendInquirySubmitted(101L, 10L);
    }

    @Test
    @DisplayName("문의 생성은 쿨다운 중이면 문의 예외를 던진다")
    void createInquiry_shouldThrowWhenCooldownExists() {
        CreateInquiryRequestDto request = CreateInquiryRequestDto.builder()
                .title("문의")
                .content("내용")
                .type(InquiryType.ACCOUNT)
                .build();
        when(userInternalService.findUserById(1L)).thenReturn(activeUser());
        when(stringRedisTemplate.hasKey("inquiry:cooldown:1")).thenReturn(true);

        assertThatThrownBy(() -> inquiryCommandService.createInquiry(1L, request))
                .isInstanceOf(InquiryException.class);
    }

    @Test
    @DisplayName("문의 취소는 본인 문의가 취소 가능 상태이면 철회 처리한다")
    void cancelInquiry_shouldWithdrawInquiry() {
        Inquiry inquiry = inquiry(1L, 10L, InquiryAnswerStatus.PENDING);
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        CancelInquiryResponseDto result = inquiryCommandService.cancelInquiry(1L, 10L);

        assertThat(result.inquiryId()).isEqualTo(10L);
        assertThat(inquiry.getAnswerStatus()).isEqualTo(InquiryAnswerStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("문의 취소는 다른 사용자의 문의이면 문의 예외를 던진다")
    void cancelInquiry_shouldThrowWhenNotOwner() {
        Inquiry inquiry = inquiry(2L, 10L, InquiryAnswerStatus.PENDING);
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryCommandService.cancelInquiry(1L, 10L))
                .isInstanceOf(InquiryException.class);
    }

    private User activeUser() {
        return User.builder()
                .email("user@test.ac.kr")
                .password("password")
                .name("사용자")
                .nickname("tester")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
    }

    private Inquiry inquiry(Long userId, Long inquiryId, InquiryAnswerStatus status) {
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title("문의")
                .content("문의 내용")
                .inquiryType(InquiryType.PAYMENT)
                .build();
        ReflectionTestUtils.setField(inquiry, "id", inquiryId);
        ReflectionTestUtils.setField(inquiry, "answerStatus", status);
        return inquiry;
    }
}
