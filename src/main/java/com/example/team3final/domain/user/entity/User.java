package com.example.team3final.domain.user.entity;

import com.example.team3final.common.entity.BaseEntity;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email; // 검증된 Email

    @Column(nullable = false)
    private String password; // BCrypt로 암호화된 값이 저장

    @Column(nullable = false, length = 50)
    private String name; // 실명

    @Column(nullable = false, length = 50)
    private String nickname; // 게시글에서 사용할 닉네임

    @Column(nullable = false)
    private Long universityId; // 검증된 학교 ID

    @Column(nullable = false, length = 100)
    private String major; // 학과

    @Column(nullable = false, length = 20)
    private String studentNumber; //학번 입학연도 기준 뒤에 2자리

    @Column(nullable = false)
    private LocalDate birthDate; // 생년

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender; // 성별

    @Column(nullable = false)
    private int point; // 보유 포인트

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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



}
