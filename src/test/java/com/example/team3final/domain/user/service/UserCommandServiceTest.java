package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandService 단위 테스트")
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserCommandServiceImpl userCommandService;

    @Test
    @DisplayName("사용자 생성은 가입 보너스를 지급하고 포인트 거래 내역을 저장한다")
    void createUser_shouldGiveSignupBonusAndSavePointTransaction() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);

        User result = userCommandService.createUser(
                "user@test.ac.kr",
                "encoded-password",
                "사용자",
                "tester",
                1L,
                "컴퓨터공학",
                "20",
                LocalDate.of(2000, 1, 1),
                Gender.MALE);

        assertThat(result.getFreePoint()).isEqualTo(10000);
        verify(pointTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(10000);
    }

    @Test
    @DisplayName("사용자 조회는 사용자 ID로 내 정보를 반환한다")
    void getUser_shouldReturnUser() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        GetUserResponseDto result = userCommandService.getUser(1L);

        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("사용자 수정은 닉네임과 전공을 변경한다")
    void updateUser_shouldUpdateNicknameAndMajor() {
        User user = user(1L);
        UpdateUserRequestDto request = UpdateUserRequestDto.builder()
                .nickname("newNick")
                .major("소프트웨어학")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("newNick")).thenReturn(false);

        UpdateUserResponseDto result = userCommandService.updateUser(1L, request);

        assertThat(result.nickname()).isEqualTo("newNick");
        assertThat(user.getMajor()).isEqualTo("소프트웨어학");
    }

    @Test
    @DisplayName("사용자 수정은 변경할 필드가 없으면 사용자 예외를 던진다")
    void updateUser_shouldThrowWhenNoFieldToUpdate() {
        UpdateUserRequestDto request = UpdateUserRequestDto.builder().build();

        assertThatThrownBy(() -> userCommandService.updateUser(1L, request))
                .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("회원 탈퇴는 비밀번호 검증 후 사용자 상태를 탈퇴로 변경한다")
    void withdrawUser_shouldWithdrawUser() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        userCommandService.withdrawUser(1L, "password");

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("user@test.ac.kr")
                .password("encoded-password")
                .name("사용자")
                .nickname("tester")
                .universityId(1L)
                .major("컴퓨터공학")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
