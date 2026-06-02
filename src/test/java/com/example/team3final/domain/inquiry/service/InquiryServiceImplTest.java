package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.admin.inquiryAnswer.service.InquiryAnswerService;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InquiryServiceImplTest {

    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private UserService userService;
    @Mock
    private InquiryAnswerService inquiryAnswerService;
    @Mock
    private AdminService adminService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private InquiryServiceImpl inquiryService;

    @Test
    @DisplayName("문의 접수 - 성공")
    void createInquiry_Success() {
        // given
        given(stringRedisTemplate.hasKey(anyString())).willReturn(false);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("0");
        given(inquiryRepository.existsByUserIdAndInquiryTypeAndAnswerStatusIn(anyLong(), any(), anyList())).willReturn(false);

        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getId()).willReturn(1L);
        given(inquiryRepository.save(any(Inquiry.class))).willReturn(inquiry);
        given(adminService.getAdminId()).willReturn(999L);

        CreateInquiryRequestDto request = new CreateInquiryRequestDto();
        ReflectionTestUtils.setField(request, "title", "Title");
        ReflectionTestUtils.setField(request, "content", "Content");
        ReflectionTestUtils.setField(request, "type", InquiryType.USAGE);

        // when
        CreateInquiryResponseDto result = inquiryService.createInquiry(1L, request);

        // then
        assertNotNull(result);
        verify(inquiryRepository).save(any(Inquiry.class));
        verify(notificationPublisher).sendInquirySubmitted(999L, 1L);
    }

    @Test
    @DisplayName("문의 접수 - 실패 (쿨다운)")
    void createInquiry_Fail_Cooldown() {
        // given
        given(stringRedisTemplate.hasKey("inquiry:cooldown:1")).willReturn(true);

        CreateInquiryRequestDto request = new CreateInquiryRequestDto();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.InquiryException.class, () -> {
            inquiryService.createInquiry(1L, request);
        });
    }

    @Test
    @DisplayName("문의 상세 조회 - 성공")
    void getOneInquiry_Success() {
        // given
        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getUserId()).willReturn(1L);
        given(inquiryRepository.findById(100L)).willReturn(Optional.of(inquiry));
        given(inquiryAnswerService.getByInquiryId(100L)).willReturn(Optional.empty());

        // when
        GetOneInquiryResponseDto result = inquiryService.getOneInquiry(1L, 100L);

        // then
        assertNotNull(result);
    }

    @Test
    @DisplayName("문의 상세 조회 - 실패 (권한 없음)")
    void getOneInquiry_Fail_AccessDenied() {
        // given
        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getUserId()).willReturn(2L); // Different user
        given(inquiryRepository.findById(100L)).willReturn(Optional.of(inquiry));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.InquiryException.class, () -> {
            inquiryService.getOneInquiry(1L, 100L);
        });
    }
}
