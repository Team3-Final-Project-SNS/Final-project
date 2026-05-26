package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.meet.service.MeetVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionTimeoutScheduler {

    private final MeetVerificationService meetVerificationService;

    // 1분마다 실행 -> REQUESTED 상태 + 5분 초과 ->  EXPIRED 일괄 처리
    // fixedDelay 이전 실행 완료 후 1분 뒤 실행 (동시 실행 방지)
    @Scheduled(fixedDelay = 60000)
    public void expireTimeoutExtensions() {
        log.debug("[ExtensionTimeoutScheduler] 연장 요청 타임아웃 체크 실행");
        meetVerificationService.expireTimeoutExtensions();
    }
}
