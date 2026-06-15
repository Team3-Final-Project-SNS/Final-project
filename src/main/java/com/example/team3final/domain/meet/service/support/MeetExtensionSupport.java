package com.example.team3final.domain.meet.service.support;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 만남 연장 보조 컴포넌트
@Component
@RequiredArgsConstructor
public class MeetExtensionSupport {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final StringRedisTemplate redisTemplate;

    // 그룹 연장 요청의 만료 여부를 확인
    // 만료된 상태라면 같은 Post의 모든 REQUESTED MeetVerification을 EXPIRED 처리하고 예외를 던짐
    public void validateGroupExtensionNotExpired(Long postId, MeetVerification meetVerification) {

        // 요청 기준 MV가 REQUESTED 상태이고 5분 타임아웃이 지났는지 확인
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED
                && meetVerification.isExtensionExpired(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES)) {

            // 같은 Post에 속한 모든 Match ID를 조회
            List<Long> siblingMatchIds = matchInternalService.getMatchIdsByPostId(postId);

            // 요청 기준 MV가 이미 만료된 상태이므로, 대상 Match가 없더라도 수락/거절 흐름은 차단
            if (siblingMatchIds.isEmpty()) {
                throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
            }

            // 같은 Post에 속한 모든 MeetVerification을 PESSIMISTIC_WRITE 락으로 조회
            // 수락/거절 요청과 만료 처리가 동시에 들어와도, 한쪽 트랜잭션이 상태 변경을 끝낼 때까지 다른 쪽은 대기
            List<MeetVerification> siblingMvList =
                    meetVerificationRepository.findAllByMatchIdInWithLock(siblingMatchIds);

            // 같은 Post의 REQUESTED 상태를 모두 EXPIRED 처리
            for (MeetVerification mv : siblingMvList) {

                // REQUESTED 상태인 항목만 만료 처리
                if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED) {
                    mv.expireExtension();
                }

                // 각 MV의 타임아웃 예약을 제거
                removeExtensionTimeout(mv);
            }

            // 호출부에 만료 예외를 알림
            throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
        }
    }

    // 연장 요청 타임아웃을 ZSet에 예약
    public void reserveExtensionTimeout(MeetVerification meetVerification) {

        // 현재 시각 + 5분을 Unix Timestamp로 변환
        double timeoutScore = LocalDateTime.now()
                .plusMinutes(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES)
                .toEpochSecond(MeetVerificationPolicy.KST);

        // ZSet member는 MeetVerification ID
        // 기존 스케줄러 구조가 MeetVerification ID를 기준으로 만료 대상을 찾기 때문
        redisTemplate.opsForZSet().add(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId()),
                timeoutScore
        );
    }

    // 연장 요청 타임아웃 예약을 ZSet에서 제거
    public void removeExtensionTimeout(MeetVerification meetVerification) {

        // ZSet member는 MeetVerification ID
        redisTemplate.opsForZSet().remove(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId())
        );
    }

    // 같은 Post의 모든 활성 MeetVerification이 연장 요청 가능한 상태인지 검증
    // 한 신청자가 요청해도 전체 연장으로 처리되므로, 전체 상태가 요청 가능해야 함
    public void validateGroupExtensionRequestable(List<MeetVerification> activeMvList) {

        // 활성 MV가 없다면 연장 요청을 진행할 수 없음
        if (activeMvList.isEmpty()) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 같은 Post의 모든 활성 MV 상태를 확인
        for (MeetVerification mv : activeMvList) {

            // 이미 연장이 수락되어 extendedMeetAt이 설정된 상태라면 재연장을 허용하지 않음
            if (mv.isExtended()) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_ACCEPTED);
            }

            // 이미 거절된 요청이 있다면 정책상 재요청을 허용하지 않음
            if (mv.getExtensionStatus() == ExtensionStatus.REJECTED) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REJECTED);
            }

            // 아직 만료되지 않은 REQUESTED 요청이 있으면 중복 요청으로 판단
            if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED
                    && !mv.isExtensionExpired(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES)) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REQUESTED);
            }

            // REQUESTED 상태지만 이미 만료된 요청이면 EXPIRED 처리 후 새 요청을 허용
            if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED
                    && mv.isExtensionExpired(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES)) {
                mv.expireExtension();
                removeExtensionTimeout(mv);
            }
        }
    }
}
