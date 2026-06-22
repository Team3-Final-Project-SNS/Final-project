package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserModerationService 테스트")
class UserModerationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserModerationServiceImpl userModerationService;

    @Test
    @DisplayName("관리자 사용자 목록 조회는 상태와 검색어 필터를 저장소에 위임한다")
    void getUsersForAdmin_shouldDelegateWithFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(userRepository.findAllByForAdmin(UserStatus.ACTIVE, "닉", pageable)).thenReturn(Page.empty(pageable));

        userModerationService.getUsersForAdmin(UserStatus.ACTIVE, "닉", pageable);

        verify(userRepository).findAllByForAdmin(UserStatus.ACTIVE, "닉", pageable);
    }

    @Test
    @DisplayName("사용자 정지를 요청하면 사용자 상태를 정지로 변경한다")
    void suspendUser_shouldChangeUserStatus() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userModerationService.suspendUser(1L, 3);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("사용자가 없으면 사용자 정지에 실패한다")
    void suspendUser_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userModerationService.suspendUser(1L, 3))
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
