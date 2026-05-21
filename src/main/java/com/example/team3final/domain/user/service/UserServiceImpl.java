package com.example.team3final.domain.user.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.UserRepository;
import io.jsonwebtoken.security.Password;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
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
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
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
        savedUser.addPoint(SIGNUP_BONUS_POINT);
        // 더티체킹: @Transactional 안에서 엔티티 필드 변경 → 트랜잭션 종료 시 자동 UPDATE

        // 4단계: PointTransaction 기록
        // 팀 규칙: 모든 포인트 변동은 반드시 point_transactions 테이블에 기록
        PointTransaction signupBonus = PointTransaction.builder()
                .userId(savedUser.getId())          // 방금 저장된 User의 id
                .matchId(null)                      // 회원가입 보너스는 매칭과 무관 → null
                .amount(SIGNUP_BONUS_POINT)         // +10,000 (양수 = 적립)
                .transactionType(PointTransactionType.JOIN_BONUS) // 가입 보너스 타입
                .balanceAfter(savedUser.getPoint()) // 지급 후 잔액 (10,000)
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
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

        // Entity → ResponseDto 변환 후 반환
        return GetUserResponseDto.from(user);
    }
}
