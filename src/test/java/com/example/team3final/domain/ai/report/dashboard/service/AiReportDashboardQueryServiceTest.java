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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 신고 대시보드 조회 서비스 단위 테스트")
class AiReportDashboardQueryServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private AiReportDashboardQueryServiceImpl aiReportDashboardQueryService;

    @Test
    @DisplayName("관리자 AI 대시보드 스냅샷은 각 도메인 카운트를 조합해 반환한다")
    void getSnapshot_shouldAggregateDomainCounts() {
        when(postRepository.count()).thenReturn(10L);
        when(postRepository.countByStatus(PostStatus.OPEN)).thenReturn(3L);
        when(postRepository.countByStatus(PostStatus.MATCHED)).thenReturn(4L);
        when(postRepository.countByStatus(PostStatus.EXPIRED)).thenReturn(1L);
        when(reportRepository.count()).thenReturn(5L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(2L);
        when(reportRepository.countByStatus(ReportStatus.ACCEPTED)).thenReturn(1L);
        when(reportRepository.countByStatus(ReportStatus.REJECTED)).thenReturn(1L);
        when(inquiryRepository.count()).thenReturn(6L);
        when(inquiryRepository.countByAnswerStatus(InquiryAnswerStatus.PENDING)).thenReturn(2L);
        when(inquiryRepository.countByAnswerStatus(InquiryAnswerStatus.ANSWERED)).thenReturn(3L);
        when(disputeRepository.count()).thenReturn(7L);
        when(disputeRepository.countByStatus(DisputeStatus.SUBMITTED)).thenReturn(1L);
        when(disputeRepository.countByStatus(DisputeStatus.UNDER_REVIEW)).thenReturn(2L);
        when(disputeRepository.countByStatus(DisputeStatus.HOLD)).thenReturn(3L);
        when(disputeRepository.countByStatus(DisputeStatus.ACCEPTED)).thenReturn(1L);
        when(disputeRepository.countByStatus(DisputeStatus.PARTIALLY_ACCEPTED)).thenReturn(1L);
        when(disputeRepository.countByStatus(DisputeStatus.REJECTED)).thenReturn(1L);
        when(userRepository.count()).thenReturn(8L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(6L);
        when(userRepository.countByStatus(UserStatus.SUSPENDED)).thenReturn(1L);
        when(userRepository.countByStatus(UserStatus.WITHDRAWN)).thenReturn(1L);
        when(paymentRepository.count()).thenReturn(9L);
        when(paymentRepository.countByStatus(PaymentStatus.READY)).thenReturn(1L);
        when(paymentRepository.countByStatus(PaymentStatus.PAID)).thenReturn(4L);
        when(paymentRepository.countByStatus(PaymentStatus.CANCELLED)).thenReturn(2L);
        when(paymentRepository.countByStatus(PaymentStatus.FAILED)).thenReturn(1L);
        when(paymentRepository.sumAmountByStatus(PaymentStatus.PAID)).thenReturn(10000L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(any(PaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1L);
        when(paymentRepository.countByStatusAndCompletedAtBetween(any(PaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2L);
        when(paymentRepository.countByStatusAndCancelledAtBetween(any(PaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(3L);
        when(paymentRepository.sumAmountByStatusAndCompletedAtBetween(any(PaymentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(5000L);

        AiReportDashboardSnapshotDto response = aiReportDashboardQueryService.getSnapshot();

        assertThat(response.totalPostCount()).isEqualTo(10L);
        assertThat(response.openDisputeCount()).isEqualTo(6L);
        assertThat(response.paidPaymentAmount()).isEqualTo(10000L);
    }
}
