package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @InjectMocks
    private InquiryServiceImpl inquiryService;

    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private UserService userService;
    @Mock
    private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock
    private AdminService adminService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("문의 생성 - 성공")
    void createInquiry_Success() {
        // given
        Long userId = 1L;
        CreateInquiryRequestDto request = new CreateInquiryRequestDto("TITLE", "CONTENT", InquiryType.USAGE);

        User user = mock(User.class);
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(userService.findUserById(userId)).willReturn(user);
        given(stringRedisTemplate.hasKey(anyString())).willReturn(false);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(valueOperations.increment(anyString())).willReturn(1L);
        given(adminService.getActiveAdminIds()).willReturn(List.of(10L));

        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title("TITLE")
                .content("CONTENT")
                .inquiryType(InquiryType.USAGE)
                .build();
        ReflectionTestUtils.setField(inquiry, "id", 1L);
        given(inquiryRepository.save(any(Inquiry.class))).willReturn(inquiry);

        // when
        CreateInquiryResponseDto result = inquiryService.createInquiry(userId, request);

        // then
        assertThat(result.inquiryId()).isEqualTo(1L);
        verify(inquiryRepository).save(any(Inquiry.class));
        verify(notificationPublisher).sendInquirySubmitted(eq(10L), any());
    }

    @Test
    @DisplayName("문의 상세 조회 - 성공")
    void getOneInquiry_Success() {
        Inquiry inquiry = createInquiry(1L, 1L);
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));
        given(inquiryAnswerRepository.findByInquiryId(1L)).willReturn(Optional.empty());

        GetOneInquiryResponseDto result = inquiryService.getOneInquiry(1L, 1L);

        assertThat(result.inquiryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("문의 목록 조회 - 성공")
    void getAllInquiries_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .willReturn(new PageImpl<>(List.of(createInquiry(1L, 1L)), pageable, 1));

        PageResponseDto<GetAllInquiriesResponseDto> result = inquiryService.getAllInquiries(1L, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("문의 취소 - 성공")
    void cancelInquiry_Success() {
        Inquiry inquiry = createInquiry(1L, 1L);
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));

        CancelInquiryResponseDto result = inquiryService.cancelInquiry(1L, 1L);

        assertThat(result.inquiryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("문의 엔티티 조회 - 성공")
    void getInquiryById_Success() {
        Inquiry inquiry = createInquiry(1L, 1L);
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));

        Inquiry result = inquiryService.getInquiryById(1L);

        assertThat(result).isSameAs(inquiry);
    }

    @Test
    @DisplayName("관리자 문의 목록 조회 - 성공")
    void getInquiriesForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Inquiry> page = new PageImpl<>(List.of(createInquiry(1L, 1L)));
        given(inquiryRepository.findAllByStatusAndType(InquiryAnswerStatus.PENDING, InquiryType.USAGE, pageable)).willReturn(page);

        Page<Inquiry> result = inquiryService.getInquiriesForAdmin(InquiryAnswerStatus.PENDING, InquiryType.USAGE, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private Inquiry createInquiry(Long id, Long userId) {
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title("TITLE")
                .content("CONTENT")
                .inquiryType(InquiryType.USAGE)
                .build();
        ReflectionTestUtils.setField(inquiry, "id", id);
        return inquiry;
    }
}
