package com.example.team3final.domain.ai.report.dashboard.service;

import com.example.team3final.domain.ai.report.dashboard.dto.AiReportDashboardSnapshotDto;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 콘솔 AI 답변에 필요한 운영 카운트를 조회합니다.
 *
 * 이 서비스는 실제 관리자 화면 기능을 실행하지 않고, 챗봇 답변에 필요한 읽기 전용 요약만 제공합니다.
 * Repository 접근은 이 구현체 안에 두고, AiReportTool은 서비스만 호출하도록 유지합니다.
 *
 * 주의:
 * - AI_report 도메인이지만 관리자 홈 챗봇 답변을 위해 post/report/inquiry/user/payment 도메인의
 *   집계 Repository 메서드를 읽기 전용으로 호출합니다.
 * - 다른 도메인의 상태를 변경하지 않으며, 도메인 비즈니스 처리는 각 관리자 메뉴 서비스에서 수행합니다.
 * - 여기서는 "현재 몇 건인가" 같은 대시보드 숫자만 모아 LLM Tool 컨텍스트로 전달합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReportDashboardQueryServiceImpl implements AiReportDashboardQueryService {

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final InquiryRepository inquiryRepository;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public AiReportDashboardSnapshotDto getSnapshot() {
        // 현재 홈 챗봇에서 필요한 최소 지표만 조회합니다.
        // 상세 목록이나 개인정보는 각 관리자 메뉴 API에서 확인하게 하고, AI에는 카운트만 전달합니다.
        long submittedDisputeCount = disputeRepository.countByStatus(DisputeStatus.SUBMITTED);
        long underReviewDisputeCount = disputeRepository.countByStatus(DisputeStatus.UNDER_REVIEW);
        long holdDisputeCount = disputeRepository.countByStatus(DisputeStatus.HOLD);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        return new AiReportDashboardSnapshotDto(
                postRepository.count(),
                postRepository.countByStatus(PostStatus.OPEN),
                postRepository.countByStatus(PostStatus.MATCHED),
                postRepository.countByStatus(PostStatus.EXPIRED),
                reportRepository.count(),
                reportRepository.countByStatus(ReportStatus.PENDING),
                reportRepository.countByStatus(ReportStatus.ACCEPTED),
                reportRepository.countByStatus(ReportStatus.REJECTED),
                inquiryRepository.count(),
                inquiryRepository.countByAnswerStatus(InquiryAnswerStatus.PENDING),
                inquiryRepository.countByAnswerStatus(InquiryAnswerStatus.ANSWERED),
                disputeRepository.count(),
                submittedDisputeCount + underReviewDisputeCount + holdDisputeCount,
                submittedDisputeCount,
                underReviewDisputeCount,
                holdDisputeCount,
                disputeRepository.countByStatus(DisputeStatus.ACCEPTED),
                disputeRepository.countByStatus(DisputeStatus.PARTIALLY_ACCEPTED),
                disputeRepository.countByStatus(DisputeStatus.REJECTED),
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countByStatus(UserStatus.WITHDRAWN),
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.READY),
                paymentRepository.countByStatus(PaymentStatus.PAID),
                paymentRepository.countByStatus(PaymentStatus.CANCELLED),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.sumAmountByStatus(PaymentStatus.PAID),
                paymentRepository.countByStatusAndCreatedAtBetween(PaymentStatus.READY, todayStart, tomorrowStart),
                paymentRepository.countByStatusAndCompletedAtBetween(PaymentStatus.PAID, todayStart, tomorrowStart),
                paymentRepository.countByStatusAndCancelledAtBetween(PaymentStatus.CANCELLED, todayStart, tomorrowStart),
                paymentRepository.countByStatusAndCreatedAtBetween(PaymentStatus.FAILED, todayStart, tomorrowStart),
                paymentRepository.sumAmountByStatusAndCompletedAtBetween(PaymentStatus.PAID, todayStart, tomorrowStart)
        );
    }
}
