package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.inquiryAnswer.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.service.InquiryService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.dto.response.AdminUserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInquiryAnswerServiceTest {

    @InjectMocks
    private AdminInquiryAnswerServiceImpl adminInquiryAnswerService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private InquiryAnswerRepository inquiryAnswerRepository;
    @Mock
    private InquiryService inquiryService;
    @Mock
    private UserService userService;
    @Mock
    private UniversityService universityService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("관리자 문의 목록 조회 - 성공")
    void getInquiries_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Inquiry inquiry = Inquiry.builder().userId(10L).title("title").build();
        Page<Inquiry> page = new PageImpl<>(List.of(inquiry), pageable, 1);
        given(inquiryService.getInquiriesForAdmin(any(), any(), any())).willReturn(page);
        given(userService.getUserNicknameMap(any())).willReturn(Map.of(10L, "nickname"));

        // when
        PageResponseDto<AdminGetInquiriesResponseDto> result = adminInquiryAnswerService.getInquiries(adminId, null, null, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).userNickname()).isEqualTo("nickname");
    }

    @Test
    @DisplayName("관리자 문의 상세 조회 - 성공")
    void getInquiry_Success() {
        // given
        Long adminId = 1L;
        Long inquiryId = 100L;
        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Inquiry inquiry = Inquiry.builder().userId(10L).title("title").content("content").build();
        given(inquiryService.getInquiryById(inquiryId)).willReturn(inquiry);

        AdminUserInfoDto userInfoDto = new AdminUserInfoDto(1L, "nickname", "email", 1L);
        given(userService.getAdminUserInfo(anyLong())).willReturn(userInfoDto);
        given(universityService.getUniversityName(anyList())).willReturn(Map.of(1L, "University"));
        given(inquiryAnswerRepository.findByInquiryId(inquiryId)).willReturn(Optional.empty());

        // when
        AdminGetInquiryResponseDto result = adminInquiryAnswerService.getInquiry(adminId, inquiryId);

        // then
        assertThat(result.title()).isEqualTo("title");
        assertThat(result.userNickname()).isEqualTo("nickname");
        assertThat(result.universityName()).isEqualTo("University");
    }

    @Test
    @DisplayName("고객 문의 답변 생성 - 성공")
    void createAnswer_Success() {
        // given
        Long adminId = 1L;
        Long inquiryId = 100L;
        AdminCreateInquiryRequestDto requestDto = new AdminCreateInquiryRequestDto("answer content");

        Admin admin = Admin.builder()
                .email("admin@test.com")
                .password("password")
                .name("AdminName")
                .role(com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN)
                .build();
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));

        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getAnswerStatus()).willReturn(InquiryAnswerStatus.PENDING);
        given(inquiry.getUserId()).willReturn(10L);
        given(inquiryService.getInquiryById(inquiryId)).willReturn(inquiry);

        // when
        AdminCreateInquiryResponseDto result = adminInquiryAnswerService.createAnswer(adminId, inquiryId, requestDto);

        // then
        verify(inquiryAnswerRepository).save(any(InquiryAnswer.class));
        verify(inquiry).answer();
        verify(notificationPublisher).sendInquiryAnswered(eq(10L), eq(inquiryId));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("관리자 조회 실패 시 예외 발생")
    void getInquiries_Fail_AdminNotFound() {
        // given
        Long adminId = 1L;
        given(adminRepository.findById(adminId)).willReturn(Optional.empty());

        // when & then
        Assertions.assertThrows(AdminException.class, () -> {
            adminInquiryAnswerService.getInquiries(adminId, null, null, PageRequest.of(0, 10));
        });
    }

    @Test
    @DisplayName("이미 답변된 문의에 답변 시 예외 발생")
    void createAnswer_Fail_AlreadyAnswered() {
        // given
        Long adminId = 1L;
        Long inquiryId = 100L;
        AdminCreateInquiryRequestDto requestDto = new AdminCreateInquiryRequestDto("answer content");

        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getAnswerStatus()).willReturn(InquiryAnswerStatus.ANSWERED);
        given(inquiryService.getInquiryById(inquiryId)).willReturn(inquiry);

        // when & then
        Assertions.assertThrows(AdminException.class, () -> {
            adminInquiryAnswerService.createAnswer(adminId, inquiryId, requestDto);
        });
    }

    @Test
    @DisplayName("문의 답변 조회 - 성공")
    void getByInquiryId_Success() {
        InquiryAnswer inquiryAnswer = mock(InquiryAnswer.class);
        given(inquiryAnswerRepository.findByInquiryId(100L)).willReturn(Optional.of(inquiryAnswer));

        Optional<InquiryAnswer> result = adminInquiryAnswerService.getByInquiryId(100L);

        assertThat(result).contains(inquiryAnswer);
    }
}
