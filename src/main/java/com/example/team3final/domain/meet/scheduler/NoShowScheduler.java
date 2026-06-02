package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.meet.service.MeetVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowScheduler {

    private final MeetVerificationService meetVerificationService;

    // GPS 단계 노쇼 판정: 10분마다 실행
    // fixedDelay: 이전 실행이 끝난 후 1분 뒤에 다시 실행 (겹침 방지)
    @Scheduled(fixedDelay = 600000)
    public void judgeGpsNoShow() {
        log.info("[NoShowScheduler] GPS 노쇼 판정 실행");
        meetVerificationService.judgeGpsNoShow();
    }

    // QR 단계 노쇼 판정: 10분마다 실행
    @Scheduled(fixedDelay = 600000)
    public void judgeQrNoShow() {
        log.info("[NoShowScheduler] QR 노쇼 판정 실행");
        meetVerificationService.judgeQrNoShow();
    }

    // 노쇼 확정 판정: 10분마다 실행
    // _NO_SHOW 상태가 된 지 24시간이 지난 건 확정 처리
    @Scheduled(fixedDelay = 600000)
    public void judgeNoShowConfirmed() {
        log.info("[NoShowScheduler] 노쇼 확정 판정 실행");
        meetVerificationService.judgeNoShowConfirmed();
    }
}
