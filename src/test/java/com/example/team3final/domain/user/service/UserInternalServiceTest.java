package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserInternalService 단위 테스트")
class UserInternalServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserInternalServiceImpl userInternalService;

    @Test
    @DisplayName("이미 가입된 이메일 여부는 탈퇴 상태를 제외하고 조회한다")
    void isEmailAlreadyRegistered_shouldDelegateToRepository() {
        when(userRepository.existsByEmailAndStatusNot("user@test.ac.kr", UserStatus.WITHDRAWN)).thenReturn(true);

        boolean result = userInternalService.isEmailAlreadyRegistered("user@test.ac.kr");

        assertThat(result).isTrue();
        verify(userRepository).existsByEmailAndStatusNot("user@test.ac.kr", UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이메일로 사용자 ID를 조회한다")
    void getUserIdByEmail_shouldReturnUserId() {
        User user = user(1L);
        when(userRepository.findByEmail("user@test.ac.kr")).thenReturn(Optional.of(user));

        Long result = userInternalService.getUserIdByEmail("user@test.ac.kr");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("이메일로 사용자 ID 조회 시 사용자가 없으면 사용자 예외를 던진다")
    void getUserIdByEmail_shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("missing@test.ac.kr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userInternalService.getUserIdByEmail("missing@test.ac.kr"))
                .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("사용자 ID 목록으로 사용자 정보 Map을 조회한다")
    void getUserInfos_shouldReturnUserInfoMap() {
        User user = user(1L);
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user));

        Map<Long, UserInfoDto> result = userInternalService.getUserInfos(List.of(1L));

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).nickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("두 사용자가 같은 대학교이면 true를 반환한다")
    void isSameUniversity_shouldReturnTrueWhenSameUniversity() {
        User firstUser = user(1L);
        User secondUser = user(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(secondUser));

        boolean result = userInternalService.isSameUniversity(1L, 2L);

        assertThat(result).isTrue();
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("user" + userId + "@test.ac.kr")
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
