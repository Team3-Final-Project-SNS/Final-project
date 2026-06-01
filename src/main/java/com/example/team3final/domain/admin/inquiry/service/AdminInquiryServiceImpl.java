package com.example.team3final.domain.admin.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.inquiry.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.service.InquiryAnswerService;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.service.InquiryService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.dto.response.AdminUserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryServiceImpl implements AdminInquiryService{

    private final AdminRepository adminRepository;
    private final InquiryAnswerService inquiryAnswerService;
    private final InquiryService inquiryService;
    private final UserService userService;
    private final UniversityService universityService;
    private final NotificationPublisher notificationPublisher;

    // 고객 문의 상세 조회
    @Override
    public AdminGetInquiryResponseDto getInquiry(Long adminId, Long inquiryId) {

        // 어드민 검증
        adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 조회
        Inquiry inquiry = inquiryService.getInquiryById(inquiryId);

        // 작성자 정보 조회 - nickname, email, universityId 한 번에 조회
        AdminUserInfoDto userInfoDto = userService.getAdminUserInfo(inquiry.getUserId());

        // 대학 이름 조회
        Map<Long, String> universityNameMap = universityService.getUniversityName(
                List.of(userInfoDto.universityId())
        );

        String universityName = universityNameMap.get(userInfoDto.universityId());

        // 답변 조회
        // 응답 바디에 answer가 없으면 null, 있으면 조회 후 응답
        InquiryAnswer inquiryAnswer = inquiryAnswerService.getByInquiryId(inquiryId)
                .orElse(null);

        return AdminGetInquiryResponseDto.of(
                inquiry,
                userInfoDto.nickname(),
                userInfoDto.email(),
                universityName,
                inquiryAnswer
        );
    }

    // 고객 문의 목록 조회
    @Override
    public PageResponseDto<AdminGetInquiriesResponseDto> getInquiries(Long adminId, InquiryAnswerStatus status, InquiryType type, Pageable pageable) {

        adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 목록 조회
        Page<Inquiry> inquiries = inquiryService.getInquiriesForAdmin(status, type, pageable);

        // userId 목록 한 번에 추출
        List<Long> userIds = inquiries.getContent()
                .stream()
                .map(Inquiry::getUserId)
                .distinct()
                .toList();

        Map<Long, String> nicknameMap = userService.getUserNicknameMap(userIds);

        // DTO 변환
        Page<AdminGetInquiriesResponseDto> response = inquiries.map(inquiry ->
                AdminGetInquiriesResponseDto.of(
                        inquiry, nicknameMap.getOrDefault(inquiry.getUserId(), null)
                )
        );

        return PageResponseDto.from(response);
    }

    // 고객 문의 답변
    @Override
    @Transactional
    public AdminCreateInquiryResponseDto createAnswer(Long adminId, Long inquiryId, AdminCreateInquiryRequestDto requestDto) {

        // 어드민 확인
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 존재 확인
        Inquiry inquiry = inquiryService.getInquiryById(inquiryId);

        // 이미 답변 완료된 문의인지 확인
        if (inquiry.getAnswerStatus() == InquiryAnswerStatus.ANSWERED) {
            throw new AdminException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        // 답변 생성 + 저장
        InquiryAnswer inquiryAnswer = inquiryAnswerService.createAnswer(
                inquiryId,
                adminId,
                admin.getName(),
                requestDto.getContent()
        );

        // Inquiry 엔티티로 답변 완료 상태 전달
        inquiry.answer();

        // 문의 작성자에게 답변 완료 알림 발송
        notificationPublisher.sendInquiryAnswered(inquiry.getUserId(), inquiryId);

        // DTO 변환
        return AdminCreateInquiryResponseDto.from(inquiryAnswer);
    }

}
