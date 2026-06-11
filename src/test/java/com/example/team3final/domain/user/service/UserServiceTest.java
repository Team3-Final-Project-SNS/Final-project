package com.example.team3final.domain.user.service;

import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.response.AdminUserInfoDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.dto.response.UpdateUserResponseDto;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("내 정보 조회 - 성공")
    void getUser_Success() {
        // given
        Long userId = 1L;
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("test@univ.ac.kr");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        GetUserResponseDto result = userService.getUser(userId);

        // then
        assertThat(result.email()).isEqualTo("test@univ.ac.kr");
    }

    @Test
    @DisplayName("회원 생성 - 성공")
    void createUser_Success() {
        User savedUser = mock(User.class);
        given(savedUser.getId()).willReturn(1L);
        given(savedUser.getTotalPoint()).willReturn(10000);
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        User result = userService.createUser(
                "test@univ.ac.kr",
                "encoded",
                "name",
                "nickname",
                1L,
                "major",
                "24",
                LocalDate.of(2000, 1, 1),
                Gender.MALE
        );

        assertThat(result).isSameAs(savedUser);
        verify(savedUser).addFreePoint(10000);
        verify(pointTransactionRepository).save(any());
    }

    @Test
    @DisplayName("회원 수정 - 성공")
    void updateUser_Success() {
        User user = createUser(1L, "old@univ.ac.kr", "oldNick", 1L);
        UpdateUserRequestDto request = UpdateUserRequestDto.builder()
                .currentPassword("oldPassword")
                .newPassword("newPassword")
                .nickname("newNick")
                .major("newMajor")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPassword", "encoded")).willReturn(true);
        given(passwordEncoder.matches("newPassword", "encoded")).willReturn(false);
        given(passwordEncoder.encode("newPassword")).willReturn("encodedNew");
        given(userRepository.existsByNickname("newNick")).willReturn(false);

        UpdateUserResponseDto result = userService.updateUser(1L, request);

        assertThat(result.nickname()).isEqualTo("newNick");
        assertThat(result.major()).isEqualTo("newMajor");
        assertThat(result.passwordChanged()).isTrue();
    }

    @Test
    @DisplayName("회원 수정 - 변경 필드 없음")
    void updateUser_NoField_ThrowsException() {
        UpdateUserRequestDto request = UpdateUserRequestDto.builder().build();

        assertThatThrownBy(() -> userService.updateUser(1L, request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("회원 탈퇴 - 성공")
    void withdrawUser_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("raw", "encoded")).willReturn(true);

        userService.withdrawUser(1L, "raw");

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이메일 중복 확인 - 성공")
    void isEmailAlreadyRegistered_Success() {
        given(userRepository.existsByEmailAndStatusNot("test@univ.ac.kr", UserStatus.WITHDRAWN)).willReturn(true);

        boolean result = userService.isEmailAlreadyRegistered("test@univ.ac.kr");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 성공")
    void existsByNickname_Success() {
        given(userRepository.existsByNickname("nick")).willReturn(true);

        boolean result = userService.existsByNickname("nick");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이메일로 회원 ID 조회 - 성공")
    void getUserIdByEmail_Success() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findByEmail("test@univ.ac.kr")).willReturn(Optional.of(user));

        Long result = userService.getUserIdByEmail("test@univ.ac.kr");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("회원 ID로 이메일 조회 - 성공")
    void getEmailByUserId_Success() {
        User user = mock(User.class);
        given(user.getEmail()).willReturn("test@univ.ac.kr");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        String result = userService.getEmailByUserId(1L);

        assertThat(result).isEqualTo("test@univ.ac.kr");
    }

    @Test
    @DisplayName("이메일로 회원 조회 - 성공")
    void findByEmail_Success() {
        User user = mock(User.class);
        given(userRepository.findByEmail("test@univ.ac.kr")).willReturn(Optional.of(user));

        User result = userService.findByEmail("test@univ.ac.kr");

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("학교별 회원 ID 조회 - 성공")
    void getUserIdsByUniversityId_Success() {
        given(userRepository.findIdsByUniversityId(1L)).willReturn(List.of(1L, 2L));

        List<Long> result = userService.getUserIdsByUniversityId(1L);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("학교별 활성 회원 ID 조회 - 성공")
    void getActiveUserIdsByUniversityId_Success() {
        given(userRepository.findActiveUserIdsByUniversityId(1L)).willReturn(List.of(1L));

        List<Long> result = userService.getActiveUserIdsByUniversityId(1L);

        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("회원 정보 맵 조회 - 성공")
    void getUserInfos_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 10L);
        given(userRepository.findAllById(List.of(1L))).willReturn(List.of(user));

        Map<Long, UserInfoDto> result = userService.getUserInfos(List.of(1L));

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).nickname()).isEqualTo("nick");
    }

    @Test
    @DisplayName("관리자 회원 목록 조회 - 성공")
    void getUsersForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(mock(User.class)));
        given(userRepository.findAllByForAdmin(UserStatus.ACTIVE, "keyword", pageable)).willReturn(page);

        Page<User> result = userService.getUsersForAdmin(UserStatus.ACTIVE, "keyword", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 엔티티 조회 - 성공")
    void findUserById_Success() {
        User user = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        User result = userService.findUserById(1L);

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("회원 정지 - 성공")
    void suspendUser_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.suspendUser(1L, 7);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("회원 닉네임 맵 조회 - 성공")
    void getUserNicknameMap_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findAllById(List.of(1L))).willReturn(List.of(user));

        Map<Long, String> result = userService.getUserNicknameMap(List.of(1L));

        assertThat(result).containsEntry(1L, "nick");
    }

    @Test
    @DisplayName("관리자 회원 상세 조회 - 성공")
    void getAdminUserInfo_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        AdminUserInfoDto result = userService.getAdminUserInfo(1L);

        assertThat(result.email()).isEqualTo("test@univ.ac.kr");
    }

    @Test
    @DisplayName("신고 기능 제한 - 성공")
    void banReportFeature_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.banReportFeature(1L, 3);

        assertThat(user.isReportBanned()).isTrue();
    }

    @Test
    @DisplayName("신고 기능 제한 여부 조회 - 성공")
    void isReportBanned_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        user.banReport(3);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        boolean result = userService.isReportBanned(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 학교 여부 조회 - 성공")
    void isSameUniversity_Success() {
        given(userRepository.findById(1L)).willReturn(Optional.of(createUser(1L, "a@univ.ac.kr", "a", 10L)));
        given(userRepository.findById(2L)).willReturn(Optional.of(createUser(2L, "b@univ.ac.kr", "b", 10L)));

        boolean result = userService.isSameUniversity(1L, 2L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("매너 온도 수정 - 성공")
    void updateMannerTemperature_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.updateMannerTemperature(1L, new BigDecimal("40.0"));

        assertThat(user.getMannerTemperature()).isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("매너 온도 조회 - 성공")
    void getMannerTemperature_Success() {
        User user = createUser(1L, "test@univ.ac.kr", "nick", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        BigDecimal result = userService.getMannerTemperature(1L);

        assertThat(result).isEqualByComparingTo("36.5");
    }

    @Test
    @DisplayName("닉네임으로 회원 ID 조회 - 성공")
    void getUserIdsByNickname_Success() {
        given(userRepository.findIdsByNicknameLike("nick")).willReturn(List.of(1L, 2L));

        List<Long> result = userService.getUserIdsByNickname("nick");

        assertThat(result).containsExactly(1L, 2L);
    }

    private User createUser(Long id, String email, String nickname, Long universityId) {
        User user = User.builder()
                .email(email)
                .password("encoded")
                .name("name")
                .nickname(nickname)
                .universityId(universityId)
                .major("major")
                .studentNumber("24")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
