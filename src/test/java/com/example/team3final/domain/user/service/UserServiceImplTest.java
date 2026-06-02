package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("회원가입 처리 - 성공")
    void createUser_Success() {
        // given
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getTotalPoint()).willReturn(10000);
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        User result = userService.createUser("test@test.ac.kr", "encodedPw", "Name", "Nick", 1L, "Major", "2024", LocalDate.now(), Gender.MALE);

        // then
        assertNotNull(result);
        verify(userRepository).save(any(User.class));
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("내 정보 조회 - 성공")
    void getUser_Success() {
        // given
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getNickname()).willReturn("Nick");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        GetUserResponseDto result = userService.getUser(1L);

        // then
        assertNotNull(result);
        assertEquals("Nick", result.nickname());
    }

    @Test
    @DisplayName("내 정보 수정 - 비밀번호 변경 성공")
    void updateUser_PasswordChange_Success() {
        // given
        UpdateUserRequestDto request = UpdateUserRequestDto.builder()
                .currentPassword("oldPw")
                .newPassword("newPw")
                .build();
        User user = mock(User.class);
        given(user.getPassword()).willReturn("encodedOldPw");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPw", "encodedOldPw")).willReturn(true);
        given(passwordEncoder.matches("newPw", "encodedOldPw")).willReturn(false);
        given(passwordEncoder.encode("newPw")).willReturn("encodedNewPw");

        // when
        UpdateUserResponseDto result = userService.updateUser(1L, request);

        // then
        assertTrue(result.passwordChanged());
        verify(user).updatePassword("encodedNewPw");
    }

    @Test
    @DisplayName("내 정보 수정 - 닉네임 중복 실패")
    void updateUser_NicknameDuplicate_Fail() {
        // given
        UpdateUserRequestDto request = UpdateUserRequestDto.builder()
                .nickname("DuplicateNick")
                .build();
        User user = mock(User.class);
        given(user.getNickname()).willReturn("OriginalNick");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("DuplicateNick")).willReturn(true);

        // when & then
        UserException exception = assertThrows(UserException.class, () -> userService.updateUser(1L, request));
        assertEquals(ErrorCode.AUTH_NICKNAME_DUPLICATED, exception.getErrorCode());
    }

    @Test
    @DisplayName("계정 정지 - 성공")
    void suspendUser_Success() {
        // given
        User user = mock(User.class);
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.suspendUser(1L, 7);

        // then
        verify(user).suspend(7);
    }

    @Test
    @DisplayName("회원 탈퇴 - 성공")
    void withdrawUser_Success() {
        // given
        User user = mock(User.class);
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(user.getPassword()).willReturn("encodedPw");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("rawPw", "encodedPw")).willReturn(true);

        // when
        userService.withdrawUser(1L, "rawPw");

        // then
        verify(user).withdraw();
    }
}
