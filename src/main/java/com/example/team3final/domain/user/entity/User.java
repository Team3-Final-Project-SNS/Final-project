package com.example.team3final.domain.user.entity;

import com.example.team3final.common.entity.SoftDeleteEntity;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE user_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email",nullable = false, unique = true)
    private String email; // 검증된 Email

    @Column(name = "password", nullable = false)
    private String password; // BCrypt로 암호화된 값이 저장

    @Column(name = "name",nullable = false, length = 50)
    private String name; // 실명

    @Column(name = "nickname",nullable = false, length = 50)
    private String nickname; // 게시글에서 사용할 닉네임

    @Column(name = "university_id",nullable = false)
    private Long universityId; // 검증된 학교 ID

    @Column(name = "major",nullable = false, length = 100)
    private String major; // 학과

    @Column(name = "student_id_number",nullable = false, length = 20)
    private String studentNumber; //학번 입학연도 기준 뒤에 2자리

    @Column(name = "birth_date",nullable = false)
    private LocalDate birthDate; // 생년월일

    @Enumerated(EnumType.STRING)
    @Column(name = "gender",nullable = false, length = 10)
    private Gender gender; // 성별

    @Column(name = "free_point",nullable = false)
    private int freePoint; // 무료 포인트: 가입 보너스, 신고 채택 보상, 후기 포상 등. 환불 불가

    @Column(name = "paid_point", nullable = false)
    private int paidPoint; // 유료 포인트: 현금 결제로 충전된 잔액. 환불 가능(결제 취소 시)

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false, length = 20)
    private UserStatus status; // 유저의 계정 상태

    // 정지 만료 시각
    // null = 만료 날짜 없음 = 영구정지
    // 값 있음 = 해당 날짜까지만 정지
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    // 매너온도
    // BigDecimal -> 십진수 그대로 저장하기 때문에 오차가 매우 적음
    // double -> 부동 소수점 방식으로 숫자를 저장, 이진수로 변환하는 과정에서 근사값으로 저장됨 -> 오차 발생
    @Column(name = "manner_temperature", nullable = false)
    private BigDecimal mannerTemperature;

    @Builder
    private User(String email, String password, String name, String nickname,
                 Long universityId, String major, String studentNumber,
                 LocalDate birthDate, Gender gender) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.universityId = universityId; // University ID만 저장
        this.major = major;
        this.studentNumber = studentNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.freePoint = 0;      // 가입 보너스는 createUser 서비스에서 별도 처리
        this.paidPoint = 0;      // 충전 전엔 0
        this.status = UserStatus.ACTIVE; // 가입 시 기본 상태
        this.mannerTemperature = new BigDecimal("36.5");
    }

    // 포인트 차감 결과
    public record DeductResult(int fromFree, int fromPaid) {
        public int total() { return fromFree + fromPaid; }
    }

    // 닉네임 변경
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 학과 변경
    public void updateMajor(String major) {
        this.major = major;
    }

    // 비밀번호 변경(암호화된 값을 받아서 저장)
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 회원 탈퇴
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        super.delete(); // SoftDeleteEntity의 deleted_at 세팅
    }

    // 계정 활성 상태인지 확인
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // ==================== Service to Service 구현 영역 ====================

    // 무료 포인트 적립 — 가입 보너스, 신고/후기 포상, 환불 시 free 환원 등
    public void addFreePoint(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("적립 금액은 음수일 수 없습니다: " + amount);
        }
        this.freePoint += amount;
    }

    // 유료 포인트 적립 — 현금 결제로 충전된 경우에만 호출
    public void addPaidPoint(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("적립 금액은 음수일 수 없습니다: " + amount);
        }
        this.paidPoint += amount;
    }

    /**
     * 포인트 차감 — 무료 먼저, 부족분은 유료에서.
     * 반환값: 실제로 무료에서 차감된 금액, 유료에서 차감된 금액 (호출 측이 PointTransaction에 기록)
     *
     * @throws UserException 잔액이 총량보다 부족하면
     */
    public DeductResult deduct(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("차감 금액은 음수일 수 없습니다: " + amount);
        }
        int total = this.freePoint + this.paidPoint;
        if (total < amount) {
            // 잔액 부족 — 무료/유료 어느 쪽에도 시도 전에 차단
            throw new UserException(ErrorCode.POINT_NOT_ENOUGH);
        }

        // 무료 먼저 — 무료 잔액과 차감액 중 작은 쪽이 무료에서 빠짐
        int fromFree = Math.min(this.freePoint, amount);
        // 나머지를 유료에서
        int fromPaid = amount - fromFree;

        this.freePoint -= fromFree;
        this.paidPoint -= fromPaid;

        // 호출 측이 PointTransaction을 어떻게 기록할지 결정할 수 있도록 분해된 값을 돌려줌
        return new DeductResult(fromFree, fromPaid);
    }

    /**
     * 유료 포인트 회수 — 결제 취소 시 사용.
     * "충전한 만큼" 회수하되, 사용된 만큼은 회수 불가(음수 방지).
     * 반환값: 실제로 회수된 금액 (충전액보다 적을 수 있음 — 이미 일부 사용한 경우)
     */
    public int withdrawPaid(int requestedAmount) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("회수 금액은 음수일 수 없습니다: " + requestedAmount);
        }
        // 현재 paidPoint가 회수 요청보다 적으면, 가능한 만큼만 회수
        // (이미 책임비로 사용된 paid는 회수 불가 — 사용자 차익 방지)
        int actual = Math.min(this.paidPoint, requestedAmount);
        this.paidPoint -= actual;
        return actual;
    }

    // 호환성: 기존 코드가 user.getPoint()로 총잔액을 보던 곳을 위한 메서드
// 새 코드는 freePoint/paidPoint를 명시적으로 다루는 게 권장
    public int getTotalPoint() {
        return this.freePoint + this.paidPoint;
    }

    // 계정 정지 (관리자)
    // days: 정지 일수 (null = 영구정지)
    public void suspend(Integer days) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedUntil = (days != null)
                ? LocalDateTime.now().plusDays(days) // days일 후 만료
                : null;                              // 만료 날짜 없음 = 영구정지
    }

    // 매너 온도 증가
    public void addMannerTemperature(BigDecimal amount) {
        this.mannerTemperature = this.mannerTemperature.add(amount);
    }

    // 매너 온도 감소
    public void deductMannerTemperature(BigDecimal amount) {
        this.mannerTemperature = this.mannerTemperature.subtract(amount);
    }


}
