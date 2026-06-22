package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.inquiryAnswer.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.service.InquiryInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserModerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 문의 답변 서비스 단위 테스트")
class AdminInquiryAnswerServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private InquiryAnswerRepository inquiryAnswerRepository;

    @Mock
    private InquiryInternalService inquiryInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private UserModerationService userModerationService;

    @Mock
    private UniversityInternalService universityInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private AdminInquiryAnswerServiceImpl adminInquiryAnswerService;

    @Test
    @DisplayName("문의 ID로 답변을 조회하면 답변 저장소 조회 결과를 반환한다")
    void getByInquiryId_shouldReturnAnswer() {
        InquiryAnswer answer = InquiryAnswer.builder()
                .inquiryId(10L)
                .adminId(1L)
                .adminName("관리자")
                .content("답변")
                .build();
        when(inquiryAnswerRepository.findByInquiryId(10L)).thenReturn(Optional.of(answer));

        Optional<InquiryAnswer> response = adminInquiryAnswerService.getByInquiryId(10L);

        assertThat(response).contains(answer);
    }

    @Test
    @DisplayName("관리자가 문의 목록을 조회하면 문의 내부 서비스 결과를 페이지 응답으로 반환한다")
    void getInquiries_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(inquiryInternalService.getInquiriesForAdmin(InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable))
                .thenReturn(Page.empty(pageable));

        PageResponseDto<?> response = adminInquiryAnswerService.getInquiries(
                1L, InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable);

        assertThat(response.totalElements()).isZero();
        verify(inquiryInternalService).getInquiriesForAdmin(InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable);
    }

    @Test
    @DisplayName("관리자가 문의 답변을 생성하면 답변 저장과 문의 답변 완료 알림을 처리한다")
    void createAnswer_shouldSaveAnswerAndNotify() {
        Inquiry inquiry = Inquiry.builder()
                .userId(2L)
                .title("문의")
                .content("내용")
                .inquiryType(InquiryType.OTHER)
                .build();
        AdminCreateInquiryRequestDto requestDto = AdminCreateInquiryRequestDto.builder()
                .content("답변입니다")
                .build();
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(inquiryInternalService.getInquiryById(10L)).thenReturn(inquiry);
        when(inquiryAnswerRepository.save(any(InquiryAnswer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminInquiryAnswerService.createAnswer(1L, 10L, requestDto);

        assertThat(inquiry.getAnswerStatus()).isEqualTo(InquiryAnswerStatus.ANSWERED);
        verify(notificationPublisher).sendInquiryAnswered(2L, 10L);
    }

    @Test
    @DisplayName("관리자가 없으면 문의 목록 조회에 실패한다")
    void getInquiries_shouldThrowWhenAdminNotFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminInquiryAnswerService.getInquiries(1L, null, null, pageable))
                .isInstanceOf(AdminException.class);
    }

    private Admin admin() {
        return Admin.createAdmin("admin@test.com", "encoded", "관리자", AdminRole.SUPER_ADMIN);
    }
}
