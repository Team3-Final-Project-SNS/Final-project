package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int SIGNUP_BONUS_POINT = 10_000;

    // 이메일로 사용자 ID를 조회합니다.
    @Override
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }

    // 같은 학교 활성 사용자 ID 목록 조회
    @Override
    public List<Long> getUserIdsByUniversityId(Long universityId) {
        // UserRepository에서 universityId 기준으로 ACTIVE 유저 ID만 조회
        // 탈퇴/정지 유저 제외 → 게시글 목록에 노출되면 안 되는 유저 자동 필터링
        return userRepository.findIdsByUniversityId(universityId);
    }

    //회원가입 시 가입되어있는 이메일인지 검증
    @Override
    public boolean isEmailAlreadyRegistered(String email) {
        return userRepository.existsByEmail(email);
    }

    // 이메일로 User 엔티티 조회(로그인시 사용)
    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    // 닉네임 중복확인
    @Override
    public boolean existsByNickname(String nickname) {
        // UserRepository를 통해 닉네임과 학교 ID가 동시에 일치하는 유저가 있는지 확인합니다.
        return userRepository.existsByNickname(nickname);
    }

    // 회원가입 처리 (쓰기 트랜잭션)
    // @Transactional(readOnly=true) 클래스 기본값을 @Transactional로 오버라이드
    @Override
    @Transactional
    public User createUser(String email, String encodedPassword, String name, String nickname,
                           Long universityId, String major, String studentNumber,
                           LocalDate birthDate, Gender gender) {

        // 1단계: User 엔티티 생성
        // 빌더 내부에서 point=0, status=ACTIVE 로 초기화됨 (User 엔티티 생성자 참고)
        User user = User.builder()
                .email(email)
                .password(encodedPassword)  // 이미 암호화된 비밀번호 (암호화는 AuthService에서 처리)
                .name(name)
                .nickname(nickname)
                .universityId(universityId)
                .major(major)
                .studentNumber(studentNumber)
                .birthDate(birthDate)
                .gender(gender)
                .build();

        // 2단계: DB에 User 저장
        // save() 호출 시점에 INSERT 쿼리 발생 → id 부여됨
        User savedUser = userRepository.save(user);

        // 3단계: 가입 보너스 포인트 지급
        // 엔티티 내부 메서드를 통해서만 필드를 변경 (직접 필드 접근 금지)
        savedUser.addFreePoint(SIGNUP_BONUS_POINT);
        // 더티체킹: @Transactional 안에서 엔티티 필드 변경 → 트랜잭션 종료 시 자동 UPDATE

        // 4단계: PointTransaction 기록
        // 팀 규칙: 모든 포인트 변동은 반드시 point_transactions 테이블에 기록
        PointTransaction signupBonus = PointTransaction.builder()
                .userId(savedUser.getId())          // 방금 저장된 User의 id
                .matchId(null)                      // 회원가입 보너스는 매칭과 무관 → null
                .amount(SIGNUP_BONUS_POINT)         // +10,000 (양수 = 적립)
                .transactionType(PointTransactionType.JOIN_BONUS) // 가입 보너스 타입
                .balanceAfter(savedUser.getTotalPoint()) // 지급 후 잔액 (10,000)
                .pointSource(PointSource.FREE) // 가입 보너스는 무료 포인트 명시
                .description("회원가입 보너스")
                .build();

        pointTransactionRepository.save(signupBonus);

        return savedUser;
    }

    // ===== 내 정보 조회 =====
    @Override
    public GetUserResponseDto getUser(Long userId) {

        // userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // Entity → ResponseDto 변환 후 반환
        return GetUserResponseDto.of(user);
    }

    // ===== 내 정보 수정 =====
    @Override
    @Transactional // 읽기 전용 클래스 기본값을 쓰기 가능으로 오버라이드 (더티 체킹 작동)
    public UpdateUserResponseDto updateUser(Long userId, UpdateUserRequestDto request) {

        // 1단계: 수정할 필드가 최소 1개 이상인지 검증
        // 세 필드 모두 null이면 수정할 게 없으므로 에러 반환
        boolean hasNothingToUpdate =
                request.getNewPassword() == null &&
                        request.getNickname() == null &&
                        request.getMajor() == null;

        if (hasNothingToUpdate) {
            throw new UserException(ErrorCode.USER_NO_FIELD_TO_UPDATE);
        }

        // 2단계: userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 변경 여부를 추적하는 플래그
        boolean passwordChanged = false;

        // 3단계: 비밀번호 변경 처리
        // newPassword가 있을 때만 비밀번호 변경 로직 실행
        if (request.getNewPassword() != null) {

            // 현재 비밀번호 확인 - newPassword가 있으면 currentPassword는 필수
            if (request.getCurrentPassword() == null) {
                throw new UserException(ErrorCode.USER_CURRENT_PASSWORD_MISMATCH);
            }

            // BCrypt 비교: passwordEncoder.matches(입력한 평문, DB의 암호화된 값)
            // BCrypt는 단방향 해시라 복호화가 불가능 → matches()로만 비교 가능
            boolean isCurrentPasswordCorrect =
                    passwordEncoder.matches(request.getCurrentPassword(), user.getPassword());

            if (!isCurrentPasswordCorrect) {
                throw new UserException(ErrorCode.USER_CURRENT_PASSWORD_MISMATCH);
            }

            // 새 비밀번호가 현재 비밀번호와 동일한지 확인
            boolean isSamePassword =
                    passwordEncoder.matches(request.getNewPassword(), user.getPassword());

            if (isSamePassword) {
                throw new UserException(ErrorCode.USER_SAME_PASSWORD);
            }

            // 새 비밀번호 암호화 후 엔티티 메서드로 변경
            // 암호화 책임은 Service에 있음 (User 엔티티는 암호화 로직을 모름)
            String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
            user.updatePassword(encodedNewPassword); // 엔티티 도메인 메서드 호출
            passwordChanged = true;
        }

        // 4단계: 닉네임 변경 처리
        // null이 아닐 때만 변경 (PATCH 패턴: 보낸 필드만 수정)
        if (request.getNickname() != null) {

            // 닉네임 중복 확인 - 현재 내 닉네임과 동일하면 중복 체크 스킵
            boolean isDifferentNickname = !request.getNickname().equals(user.getNickname());

            if (isDifferentNickname && userRepository.existsByNickname(request.getNickname())) {
                throw new UserException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
            }

            user.updateNickname(request.getNickname()); // 엔티티 도메인 메서드 호출
        }

        // 5단계: 학과 변경 처리
        if (request.getMajor() != null) {
            user.updateMajor(request.getMajor()); // 엔티티 도메인 메서드 호출
        }

        // @Transactional + 더티 체킹 덕분에 save() 없이도 자동으로 UPDATE 쿼리 실행
        return UpdateUserResponseDto.of(user, passwordChanged);
    }

    @Override
    public Map<Long, UserInfoDto> getUserInfos(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // DB에서 IN 절로 유저들을 한 번에(Bulk) 땡겨와서 Map으로 반환
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserInfoDto::from));
    }

    // Admin 도메인에서 사용할 유저 목록 조회
    @Override
    public Page<User> getUsersForAdmin(UserStatus status, String keyword, Pageable pageable) {
        return userRepository.findAllByForAdmin(status, keyword, pageable);
    }

    // Admin 도메인에서 사용할 유저 계정 정지
    // days: 정지 일수 (null = 영구정지)
    @Override
    @Transactional
    public void suspendUser(Long userId, Integer days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 이미 정지된 계정이면 예외
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UserException(ErrorCode.ADMIN_USER_ALREADY_SUSPENDED);
        }

        // 더티체킹으로 자동 업데이트
        user.suspend(days);
    }

    // userId 목록을 닉네임 Map으로 반환
    @Override
    public Map<Long, String> getUserNicknameMap(List<Long> userIds) {

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    // 회원 탈퇴
    @Override
    @Transactional
    public void withdrawUser(Long userId, String rawPassword) {

        // 1. 유저 조회 — 없으면 USER_NOT_FOUND 예외
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        // 2. 이미 탈퇴/정지된 계정이면 진행 불가
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserException(ErrorCode.USER_SUSPENDED_OR_WITHDRAWN);
        }

        // 3. 비밀번호 일치 여부 확인
        boolean isPasswordCorrect = passwordEncoder.matches(rawPassword, user.getPassword());
        if (!isPasswordCorrect) {
            throw new UserException(ErrorCode.USER_CURRENT_PASSWORD_MISMATCH);
        }

        // 4. 상태를 WITHDRAWN으로 변경
        user.withdraw();

        // 5. 더티 체킹: @Transactional 범위 안에서 엔티티 변경 → 트랜잭션 종료 시 자동 UPDATE
        // save()를 명시적으로 호출하지 않아도 됨
    }

    // email, university를 포함한 관리자용 단건 조회
    @Override
    public AdminUserInfoDto getAdminUserInfo(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return AdminUserInfoDto.from(user);
    }


    /**
     * 두 사용자가 같은 학교 소속인지 확인합니다.
     *
     * 후기 조회 정책에서 본인이 아니더라도 같은 학교 유저의 후기는 조회할 수 있으므로,
     * Review 도메인에서 직접 UserRepository를 참조하지 않고 UserService를 통해 검증합니다.
     */
    @Override
    public boolean isSameUniversity(Long userId, Long otherUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return user.getUniversityId().equals(otherUser.getUniversityId());
    }


    /**
     * 후기 집계 결과로 사용자의 매너 온도를 갱신합니다.
     *
     * 매너 온도 계산은 Review 도메인에서 수행하고,
     * User 엔티티의 실제 값 변경은 UserService를 통해 처리합니다.
     */
    @Override
    @Transactional
    public void updateMannerTemperature(Long userId, BigDecimal mannerTemperature) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        user.updateMannerTemperature(mannerTemperature);
    }


    /**
     * 사용자의 현재 매너 온도를 조회합니다.
     *
     * 받은 후기 목록 응답에서 최신 매너 온도를 함께 내려주기 위해 사용합니다.
     */
    @Override
    public BigDecimal getMannerTemperature(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

        return user.getMannerTemperature();
    }
}
