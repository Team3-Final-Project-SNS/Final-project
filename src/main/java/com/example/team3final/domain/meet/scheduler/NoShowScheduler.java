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

    // GPS 단계 노쇼 판정: 1분마다 실행
    // 실제 노쇼 예정 기준은 meetAt + 10분이고, 배치는 1분 단위로 훑어 지연을 최소화한다.
    @Scheduled(cron = "0 * * * * *")
    public void judgeGpsNoShow() {
        log.info("[NoShowScheduler] GPS 노쇼 판정 실행");
        meetVerificationNoShowService.judgeGpsNoShow();
    }

    // QR 단계 노쇼 판정: 1분마다 실행
    // QR 유효시간이 10분이므로 만료 이후 최대한 빠르게 노쇼/취소 판정을 수행한다.
    @Scheduled(cron = "0 * * * * *")
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
