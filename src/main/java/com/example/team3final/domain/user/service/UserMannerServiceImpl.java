package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

// User 도메인의 매너온도 조회 및 변경 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMannerServiceImpl implements UserMannerService {

    private final UserRepository userRepository;

    // 매너온도 갱신 — 비관락 적용 버전
    // ReviewServiceImpl에서 계산한 temperatureDelta(온도 변동치)만 받음
    // 비관락으로 currentTemperature를 읽어 가산 → 0~99 클램핑 후 저장
    //
    // ★ 이전 버전과의 차이:
    //   이전: updateMannerTemperatureWithLock(userId, averageScoreDelta, mannerWeight)
    //         → 내부에서 currentTemperature + averageScoreDelta × mannerWeight 계산
    //         → ReviewServiceImpl에서 finalTemperature를 넘기면 이중 계산 발생
    //
    //   현재: updateMannerTemperatureWithLock(userId, temperatureDelta)
    //         → ReviewServiceImpl에서 가중치·클램핑까지 완료한 변동치만 받음
    //         → 내부에서 currentTemperature + temperatureDelta 만 계산
    //         → 이중 계산 없음
    @Override
    @Transactional
    public void updateMannerTemperatureWithLock(Long userId, BigDecimal temperatureDelta) {

        // 비관적 락으로 조회 — 이 트랜잭션이 커밋될 때까지 다른 스레드가 이 행을 수정 불가
        // 1:N 단체 만남에서 여러 신청자가 동시에 후기를 제출해도 Lost Update 방지
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 락이 걸린 상태에서 현재 온도를 읽어 변동치를 더함
        BigDecimal currentTemperature = user.getMannerTemperature();
        BigDecimal changed = currentTemperature
                .add(temperatureDelta)                    // 현재 온도 + 변동치
                .setScale(1, RoundingMode.HALF_UP);       // 소수점 첫째 자리 반올림

        // 0도 미만 → 0, 99도 초과 → 99로 클램핑
        BigDecimal clamped = changed
                .max(BigDecimal.ZERO)
                .min(new BigDecimal("99.0"));

        user.updateMannerTemperature(clamped);
    }

    // 사용자의 현재 매너온도 조회
    @Override
    public BigDecimal getMannerTemperature(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return user.getMannerTemperature();
    }
}