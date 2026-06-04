package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryAnswerServiceImpl implements AdminInquiryAnswerService {

    private final AdminRepository adminRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final InquiryService inquiryService;
    private final UserService userService;
    private final UniversityService universityService;
    private final NotificationPublisher notificationPublisher;

    // 유저용 — InquiryServiceImpl에서 유저가 본인 문의 상세 조회할 때 답변 함께 반환하기 위해 호출
    @Override
    public Optional<InquiryAnswer> getByInquiryId(Long inquiryId) {
        return inquiryAnswerRepository.findByInquiryId(inquiryId);
    }

    // 관리자 문의 상세 조회
    @Override
    public AdminGetInquiryResponseDto getInquiry(Long adminId, Long inquiryId) {

        // 어드민 존재 여부 검증
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 단건 조회
        Inquiry inquiry = inquiryService.getInquiryById(inquiryId);

        // 작성자 정보 조회 (nickname, email, universityId)
        AdminUserInfoDto userInfoDto = userService.getAdminUserInfo(inquiry.getUserId());

        // 대학 이름 조회
        Map<Long, String> universityNameMap = universityService.getUniversityName(
                List.of(userInfoDto.universityId())
        );
        String universityName = universityNameMap.get(userInfoDto.universityId());

        // 답변 조회 — 없으면 null 반환
        InquiryAnswer inquiryAnswer = inquiryAnswerRepository.findByInquiryId(inquiryId)
                .orElse(null);

        return AdminGetInquiryResponseDto.of(
                inquiry,
                userInfoDto.nickname(),
                userInfoDto.email(),
                universityName,
                inquiryAnswer
        );
    }

    // 관리자 문의 목록 조회
    @Override
    public PageResponseDto<AdminGetInquiriesResponseDto> getInquiries(
            Long adminId, InquiryAnswerStatus status, InquiryType type, Pageable pageable) {

        // 어드민 존재 여부 검증
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 목록 조회 (status, type 필터 + 페이징)
        Page<Inquiry> inquiries = inquiryService.getInquiriesForAdmin(status, type, pageable);

        // 문의 작성자 userId 목록 한 번에 추출 — N+1 방지
        List<Long> userIds = inquiries.getContent()
                .stream()
                .map(Inquiry::getUserId)
                .distinct()
                .toList();

        // userId → nickname 매핑을 한 번의 쿼리로 조회
        Map<Long, String> nicknameMap = userService.getUserNicknameMap(userIds);

        // Inquiry → DTO 변환
        Page<AdminGetInquiriesResponseDto> response = inquiries.map(inquiry ->
                AdminGetInquiriesResponseDto.of(
                        inquiry,
                        nicknameMap.getOrDefault(inquiry.getUserId(), null)
                )
        );

        return PageResponseDto.from(response);
    }

    // 고객 문의 답변 생성
    @Override
    @Transactional
    public AdminCreateInquiryResponseDto createAnswer(
            Long adminId, Long inquiryId, AdminCreateInquiryRequestDto requestDto) {

        // 어드민 존재 여부 검증
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 문의 존재 여부 확인
        Inquiry inquiry = inquiryService.getInquiryById(inquiryId);

        // 이미 답변된 문의인지 확인
        if (inquiry.getAnswerStatus() == InquiryAnswerStatus.ANSWERED) {
            throw new AdminException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        // 답변 엔티티 생성 후 저장
        InquiryAnswer inquiryAnswer = InquiryAnswer.builder()
                .inquiryId(inquiryId)
                .adminId(adminId)
                .adminName(admin.getName())
                .content(requestDto.getContent())
                .build();
        inquiryAnswerRepository.save(inquiryAnswer);

        // Inquiry 상태를 ANSWERED로 변경
        inquiry.answer();

        // 문의 작성자에게 답변 완료 알림 발송
        notificationPublisher.sendInquiryAnswered(inquiry.getUserId(), inquiryId);

        return AdminCreateInquiryResponseDto.from(inquiryAnswer);
    }
}