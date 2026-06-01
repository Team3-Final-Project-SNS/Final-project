package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.service.InquiryAnswerService;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService{

    private final InquiryRepository inquiryRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final InquiryAnswerService inquiryAnswerService;

    private static final int MAX_DAILY_INQUIRY_COUNT = 20; // 하루 최대 문의 접수 횟수
    private static final Duration COOLDOWN_DURATION =  Duration.ofMinutes(1); // 문의 간 최소 대기 시간

    private static final String DAILY_COUNT_KEY_PREFIX = "inquiry:daily:";
    private static final String COOLDOWN_KEY_PREFIX = "inquiry:cooldown:";

    // 고객 문의 접수
    @Override
    @Transactional
    public CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request) {

        // 유저 존재 여부 확인
        userService.getUserInfo(userId);

        // 1분 쿨다운 확인
        validateCooldown(userId);

        // 하루 20개 제한 확인
        validateDailyLimit(userId);

        // 같은 카테고리 중복 접수 방지 검증
        validateDuplicateActiveInquiry(userId, request.getType());

        // 검증 완료 문의 엔티티 생성
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .inquiryType(request.getType())
                .build();

        // DB에 저장
        Inquiry savedInquiry = inquiryRepository.save(inquiry);

        // Redis 업데이트
        updateRedisAfterCreate(userId);

        return CreateInquiryResponseDto.from(savedInquiry);
    }

    // 내 문의 상세(답변) 조회
    @Override
    public GetOneInquiryResponseDto getOneInquiry(Long userId, Long inquiryId) {

        // 1. 문의 존재 여부 확인
        // orElseThrow: 없으면 즉시 예외 발생 → GlobalExceptionHandler가 404로 변환
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));

        // 2. 본인 문의인지 확인 (userId 일치 여부)
        // inquiry.getUserId(): 이 문의를 접수한 유저 ID
        if (!inquiry.getUserId().equals(userId)) {
            throw new InquiryException(ErrorCode.INQUIRY_ACCESS_DENIED); // 403
        }

        // 3. 답변 조회 (없을 수 있으므로 Optional 사용)
        InquiryAnswer answer = inquiryAnswerService.getByInquiryId(inquiryId)
                .orElse(null);

        return GetOneInquiryResponseDto.of(inquiry, answer);
    }

    // 내 문의 목록 조회
    public PageResponseDto<GetAllInquiriesResponseDto> getAllInquiries(Long userId, Pageable pageable) {

        // 1. userId로 본인 문의만 최신순 페이징 조회
        Page<Inquiry> inquiryPage = inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        // 2. page<Inquiry> -> page<GetAllInquiriesResponseDto> 변환
        Page<GetAllInquiriesResponseDto> dtoPage = inquiryPage.map(GetAllInquiriesResponseDto::from);

        // 3. 팀 공통 페이징 응답 포맷으로 한번 더 변환
        return PageResponseDto.from(dtoPage);
    }

    // 고객 문의 취소
    @Override
    @Transactional
    public CancelInquiryResponseDto cancelInquiry(Long userId, Long inquiryId) {

        // 1. 문의 존재 여부 확인
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));

        //2. 본인 문의인지 확인
        if (!inquiry.getUserId().equals(userId)) {
            throw new InquiryException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }

        // 3. 취소 가능 한 상태인지 확인
        InquiryAnswerStatus status = inquiry.getAnswerStatus();
        boolean isCancellable = status == InquiryAnswerStatus.PENDING
                || status == InquiryAnswerStatus.READ;

        if (!isCancellable) {
            throw new InquiryException(ErrorCode.INQUIRY_CANCEL_FORBIDDEN);
        }

        // 4. 취소 처리
        inquiry.withdraw();

        return CancelInquiryResponseDto.from(inquiry);
    }

    // 관리자용 단건 조회
    @Override
    public Inquiry getInquiryById(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    // 관리자용 목록 조회
    @Override
    public Page<Inquiry> getInquiriesForAdmin(InquiryAnswerStatus status, InquiryType type, Pageable pageable) {
        return inquiryRepository.findAllByStatusAndType(status, type, pageable);
    }

    // ===== private 검증 메서드 =====

    // 1분 쿨다운 검증
    private void validateCooldown(Long userId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (hasCooldown) {
            throw new InquiryException(ErrorCode.INQUIRY_COOLDOWN);
        }
    }

    // 하루 20개 제한
    private void validateDailyLimit(Long userId) {
        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(dailyKey);
        int count = (countStr == null) ? 0 : Integer.parseInt(countStr);
        if (count >= MAX_DAILY_INQUIRY_COUNT) {
            throw new InquiryException(ErrorCode.INQUIRY_DAILY_LIMIT_EXCEEDED);
        }
    }

    // 같은 카테고리 중복 접수 방지
    private void validateDuplicateActiveInquiry(Long userId, InquiryType inquiryType) {

        // "처리 중"으로 간주할 상태 목록: PENDING(접수됨), IN_PROGRESS(처리중)
        // List.of()는 불변 리스트 → 실수로 수정되지 않아 안전
        List<InquiryAnswerStatus> activeStatuses = List.of(
                InquiryAnswerStatus.PENDING,
                InquiryAnswerStatus.READ
        );

        // Repository에 DB 조회 위임: 해당 유저 + 카테고리 + 처리중 상태가 존재하는지 확인
        boolean hasDuplicate = inquiryRepository.existsByUserIdAndInquiryTypeAndAnswerStatusIn(
                userId,
                inquiryType,
                activeStatuses
        );

        // 중복이 존재하면 예외 발생 → Controller까지 전파 → 409 Conflict 응답
        if (hasDuplicate) {
            throw new InquiryException(ErrorCode.INQUIRY_DUPLICATE_TYPE);
        }
    }

    // 문의 접수 성공 후 Redis 업데이트 : daily 카운트 +1, 쿨다운 키 생성
    private void updateRedisAfterCreate(Long userId) {
        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;

        // 오늘 자정까지 남은 시간 계산 (daily 키 TTL로 사용)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay(); // 내일 00:00:00
        Duration timeUntilMidnight = Duration.between(now, midnight);

        // 하루 카운트 +1
        Long newCount = stringRedisTemplate.opsForValue().increment(dailyKey);

        // 첫 문의일 때만 TTL 설정 (기존 키에 TTL 덮어쓰기 방지)
        if (Long.valueOf(1).equals(newCount)) {
            stringRedisTemplate.expire(dailyKey, timeUntilMidnight);
        }

        // 쿨다운 키 생성 (TTL 1분)
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);
    }
}
