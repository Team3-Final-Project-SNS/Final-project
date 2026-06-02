package com.example.team3final.domain.admin.inquiry.service;

import com.example.team3final.domain.admin.auth.service.AdminAuthServiceImpl;
import com.example.team3final.domain.admin.inquiryAnswer.service.InquiryAnswerService;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.service.InquiryService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.service.UserService;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceImplTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private InquiryAnswerService inquiryAnswerService;
    @Mock
    private InquiryService inquiryService;
    @Mock
    private UserService userService;
    @Mock
    private UniversityService universityService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private AdminInquiryServiceImpl adminInquiryService;

    @Test
    @DisplayName("문의 상세 조회 - 성공")
    void getInquiry_Success() {
        // given
        Long adminId = 1L;
        Long inquiryId = 100L;
        Admin admin = mock(Admin.class);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));
        
        Inquiry inquiry = mock(Inquiry.class);
        given(inquiry.getUserId()).willReturn(2L);
        given(inquiryService.getInquiryById(inquiryId)).willReturn(inquiry);
        
        given(userService.getAdminUserInfo(2L)).willReturn(new com.example.team3final.domain.user.dto.response.AdminUserInfoDto(2L, "nickname", "email@test.com", 1L));
        given(universityService.getUniversityName(java.util.List.of(1L))).willReturn(java.util.Map.of(1L, "Test Univ"));
        given(inquiryAnswerService.getByInquiryId(inquiryId)).willReturn(Optional.empty());

        // when
        var result = adminInquiryService.getInquiry(adminId, inquiryId);

        // then
        assertNotNull(result);
    }
}
