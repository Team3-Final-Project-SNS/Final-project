package com.example.team3final.domain.user.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
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

import java.time.LocalDate;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(name = "point",nullable = false)
    private int point; // 보유 포인트

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false, length = 20)
    private UserStatus status; // 유저의 계정 상태

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
        this.point = 0;                  // 기본값 0, 가입 보너스는 서비스에서 별도 처리
        this.status = UserStatus.ACTIVE; // 가입 시 기본 상태
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
    }

    // 계정 활성 상태인지 확인
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // ==================== Service to Service 구현 영역 ====================
    // 포인트 증가 (가입 보너스, 환불 등)
    // 트랜잭션 안에서 호출 — 호출 측에서 PointTransaction도 함께 저장해야 함
    public void addPoint(int amount) {
        this.point += amount;
    }

    // 포인트 차감 (예치, 패널티 등)
    // 잔액 부족이면 예외 — 호출 측에서 사전 검증하거나 이 메서드에서 던짐
    public void deductPoint(int amount) {
        if (this.point < amount) {
            throw new UserException(ErrorCode.POINT_NOT_ENOUGH
            );
        }
        this.point -= amount;
    }

    // 계정 정지 (관리자)
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }


}
