package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMannerService 단위 테스트")
class UserMannerServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserMannerServiceImpl userMannerService;

    @Test
    @DisplayName("매너온도 변경량을 비관적 락으로 조회한 사용자에게 반영한다")
    void updateMannerTemperatureWithLock_shouldApplyDelta() {
        User user = user();
        when(userRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(user));

        userMannerService.updateMannerTemperatureWithLock(1L, BigDecimal.valueOf(0.3));

        assertThat(user.getMannerTemperature()).isEqualByComparingTo("36.8");
    }

    @Test
    @DisplayName("사용자가 없으면 매너온도 조회에 실패한다")
    void getMannerTemperature_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userMannerService.getMannerTemperature(1L))
                .isInstanceOf(UserException.class);
    }

    private User user() {
        return User.builder()
                .email("user@test.com")
                .password("encoded")
                .name("사용자")
                .nickname("닉네임")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(com.example.team3final.domain.user.enums.Gender.MALE)
                .build();
    }
}
