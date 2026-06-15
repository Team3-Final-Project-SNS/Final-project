package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.meet.service.MeetVerificationNoShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowScheduler {

    private final MeetVerificationNoShowService meetVerificationNoShowService;

    // GPS 단계 노쇼 판정: 10분마다 실행
    // fixedDelay: 이전 실행이 끝난 후 1분 뒤에 다시 실행 (겹침 방지)
    @Scheduled(cron = "0 0/10 * * * *")
    public void judgeGpsNoShow() {
        log.info("[NoShowScheduler] GPS 노쇼 판정 실행");
        meetVerificationNoShowService.judgeGpsNoShow();
    }

    // QR 단계 노쇼 판정: 10분마다 실행
    @Scheduled(cron = "0 0/10 * * * *")
    public void judgeQrNoShow() {
        log.info("[NoShowScheduler] QR 노쇼 판정 실행");
        meetVerificationNoShowService.judgeQrNoShow();
    }

//    // 노쇼 확정 판정: 10분마다 실행
//    // _NO_SHOW 상태가 된 지 24시간이 지난 건 확정 처리
//    @Scheduled(cron = "0 0/10 * * * *")
//    public void judgeNoShowConfirmed() {
//        log.info("[NoShowScheduler] 노쇼 확정 판정 실행");
//        meetVerificationNoShowService.judgeNoShowConfirmed();
//    }

    // ===== 변경 (테스트용 — 배포 전 위에 주석처리된 메서드 원복 필요) =====
    @Scheduled(cron = "0 * * * * *")  // 1분마다
    public void judgeNoShowConfirmed() {
        log.info("[NoShowScheduler] 노쇼 확정 판정 실행");
        meetVerificationNoShowService.judgeNoShowConfirmed();
    }
}
