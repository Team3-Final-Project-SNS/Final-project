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

    // 후기 결과 -> 사용자 매너온도에 반영
    @Override
    @Transactional
    public void updateMannerTemperature(Long userId, BigDecimal mannerTemperature) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        user.updateMannerTemperature(mannerTemperature);
    }

    // 매너온도 갱신 - 비관락 적용 버전
    @Override
    @Transactional
    public void updateMannerTemperatureWithLock(Long userId, BigDecimal averageScoreDelta, BigDecimal mannerWeight) {
        // 비관적으로 조회 - 이 트랜잭션이 커밋될 때까지 다른 스레드가 이 행을 수정 불가
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 락이 걸린 상태에서 현재 온도를 읽어 직접 계산
        BigDecimal currentTemperature = user.getMannerTemperature();
        BigDecimal changed = currentTemperature
                .add(averageScoreDelta.multiply(mannerWeight))
                .setScale(1, RoundingMode.HALF_UP);

        // 0도 이하 -> 0, 99도 초과 -> 99로 클램핑
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
