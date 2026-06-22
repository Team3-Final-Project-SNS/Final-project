package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.exception.ErrorCode;
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
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

// Inquiry 도메인의 문의 생성/취소 등 사용자 요청 기반 변경 작업을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class InquiryCommandServiceImpl implements InquiryCommandService {

    private final InquiryRepository inquiryRepository;
    private final UserInternalService userInternalService;
    private final AdminService adminService;
    private final NotificationPublisher notificationPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_DAILY_INQUIRY_COUNT = 20;
    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(1);
    private static final String DAILY_COUNT_KEY_PREFIX = "inquiry:daily:";
    private static final String COOLDOWN_KEY_PREFIX = "inquiry:cooldown:";

    // 고객 문의 접수
    @Override
    public CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request) {

        // 유저 존재 여부 확인
        User user = userInternalService.findUserById(userId);

        // 1분 쿨다운 확인
        validateCooldown(userId);

        // 하루 20개 제한 확인
        validateDailyLimit(userId);

        // 정지 계정 카테고리 제한
        if (user.getStatus() == UserStatus.SUSPENDED
                && request.getType() != InquiryType.ACCOUNT) {
            // ACCOUNT 외 카테고리로 접수 시도 → 차단
            throw new InquiryException(ErrorCode.SUSPENDED_INQUIRY_TYPE_RESTRICTED);
        }

        // 검증 완료 후 문의 엔티티 생성
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .inquiryType(request.getType())
                .build();

        // DB에 저장
        Inquiry savedInquiry = inquiryRepository.save(inquiry);

        // Redis 업데이트
        // 쿨다운 키는 즉시 생성 (다음 요청 차단용)
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);

        // 일일 카운터 증가는 커밋 후 이벤트로 처리
        applicationEventPublisher.publishEvent(new InquiryCreatedEvent(userId));

        // 33. 문의 접수 알림 - 활성 관리자 모두에게
        adminService.getActiveAdminIds().forEach(
                adminId -> notificationPublisher.sendInquirySubmitted(adminId, savedInquiry.getId())
        );

        return CreateInquiryResponseDto.from(savedInquiry);
    }

    // 고객 문의 취소
    @Override
    public CancelInquiryResponseDto cancelInquiry(Long userId, Long inquiryId) {

        // 문의 존재 여부 확인
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));

        // 본인 문의인지 확인
        if (!inquiry.getUserId().equals(userId)) {
            throw new InquiryException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }

        // 취소 가능한 상태인지 확인 (PENDING, READ만 취소 가능)
        InquiryAnswerStatus status = inquiry.getAnswerStatus();
        boolean isCancellable = status == InquiryAnswerStatus.PENDING
                || status == InquiryAnswerStatus.READ;

        if (!isCancellable) {
            throw new InquiryException(ErrorCode.INQUIRY_CANCEL_FORBIDDEN);
        }

        // 취소 처리
        inquiry.withdraw();

        return CancelInquiryResponseDto.from(inquiry);
    }

    // ===== private 검증 메서드 =====

    // 1분 쿨다운 검증
    private void validateCooldown(Long userId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Objects.equals(hasCooldown, true)) {
            throw new InquiryException(ErrorCode.INQUIRY_COOLDOWN);
        }
    }

    // 하루 20개 제한 검증
    private void validateDailyLimit(Long userId) {
        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(dailyKey);
        int count = (countStr == null) ? 0 : Integer.parseInt(countStr);
        if (count >= MAX_DAILY_INQUIRY_COUNT) {
            throw new InquiryException(ErrorCode.INQUIRY_DAILY_LIMIT_EXCEEDED);
        }
    }
}
