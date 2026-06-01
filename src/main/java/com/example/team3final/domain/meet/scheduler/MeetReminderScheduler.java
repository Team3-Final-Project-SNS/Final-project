package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.meet.dto.response.MeetReminderResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetReminderScheduler {

    private final MeetVerificationRepository meetVerificationRepository;
    private final NotificationPublisher notificationPublisher;

    // 만남 30분 전 알림 - 1분마다 실행
    // reminder30Sent = false인 것만 조회 → 중복 발송 완벽 방지
    // MeetVerification + Match + Post JOIN으로 N+1 없이 쿼리 1번
    @Transactional
    @Scheduled(fixedDelay = 60000)
    public void sendReminder30() {
        LocalDateTime now = LocalDateTime.now();

        List<MeetReminderResponseDto> targets = meetVerificationRepository.findForReminder30(
                now.plusMinutes(29), now.plusMinutes(31));

        if (targets.isEmpty()) {
            return;
        }

        log.info("[MeetReminderScheduler] 30분 전 알림 대상: {}건", targets.size());

        for (MeetReminderResponseDto dto : targets) {
            // 등록자에게 30분 전 알림 발송
            notificationPublisher.sendMeetReminder30(dto.authorId(), dto.matchId());
            // 신청자에게 30분 전 알림 발송
            notificationPublisher.sendMeetReminder30(dto.applicantId(), dto.matchId());

            // 발송 완료 처리 - 더티체킹으로 자동 업데이트
            meetVerificationRepository.findById(dto.meetVerificationId())
                    .ifPresent(MeetVerification::markReminder30Sent);
        }
    }

    // 만남 15분 전 알림 - 1분마다 실행
    @Transactional
    @Scheduled(fixedDelay = 60000)
    public void sendReminder15() {
        LocalDateTime now = LocalDateTime.now();

        List<MeetReminderResponseDto> targets = meetVerificationRepository.findForReminder15(
                now.plusMinutes(14), now.plusMinutes(16));

        if (targets.isEmpty()) {
            return;
        }

        log.info("[MeetReminderScheduler] 15분 전 알림 대상: {}건", targets.size());

        for (MeetReminderResponseDto dto : targets) {
            notificationPublisher.sendMeetReminder15(dto.authorId(), dto.matchId());
            notificationPublisher.sendMeetReminder15(dto.applicantId(), dto.matchId());

            meetVerificationRepository.findById(dto.meetVerificationId())
                    .ifPresent(MeetVerification::markReminder15Sent);
        }
    }

    // 만남 임박 알림 - 1분마다 실행
    @Transactional
    @Scheduled(fixedDelay = 60000)
    public void sendImminent() {
        LocalDateTime now = LocalDateTime.now();

        List<MeetReminderResponseDto> targets = meetVerificationRepository.findForImminent(
                now.plusMinutes(4), now.plusMinutes(6));

        if (targets.isEmpty()) {
            return;
        }

        log.info("[MeetReminderScheduler] 임박 알림 대상: {}건", targets.size());

        for (MeetReminderResponseDto dto : targets) {
            notificationPublisher.sendMeetImminent(dto.authorId(), dto.matchId());
            notificationPublisher.sendMeetImminent(dto.applicantId(), dto.matchId());

            meetVerificationRepository.findById(dto.meetVerificationId())
                    .ifPresent(MeetVerification::markImminentSent);
        }
    }
}