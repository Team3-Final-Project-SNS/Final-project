package com.example.team3final.domain.university.entity;


import com.example.team3final.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "universities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class University extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "university_name", nullable = false, length = 100)
    private String universityName; // 한글 컬럼명 '학교명'을 코드에서는 영문 필드명으로 변환합니다.

    @Column(name = "email_domain", nullable = false, unique = true, length = 100)
    private String eDomain; // 학교 이메일 도메인입니다. 예: univ.ac.kr

    @Column(name = "is_active", nullable = false)
    private boolean isActive; // 회원가입에 사용할 수 있는 활성 학교인지 나타냅니다.

    //  '비활성화일' 컬럼.
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Builder
    private University(String universityName, String eDomain, boolean isActive, LocalDateTime deactivatedAt) {
        this.universityName = universityName;
        this.eDomain = eDomain;
        this.isActive = isActive;
        this.deactivatedAt = deactivatedAt;
    }

    // 학교를 활성화합니다.
    public void activate() {
        this.isActive = true;
        this.deactivatedAt = null;
    }

    // 학교를 비활성화하고 비활성화 시각을 기록합니다.
    public void deactivate() {
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
    }
}
