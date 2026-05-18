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
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // ERD: ID
    private Long id;

    @Column(name = "university_name", nullable = false, length = 100) // ERD: 학교명
    private String universityName;

    @Column(name = "e_domain", nullable = false, unique = true, length = 100) // ERD: 이메일 도메인
    private String e_Domain;

    @Column(name = "is_active", nullable = false) // ERD: 활성 여부
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false) // ERD: 생성일
    private LocalDateTime createdAt;

    @Column(name = "deactivated_at") // ERD: 비활성화일
    private LocalDateTime deactivatedAt;

    @Builder
    private University(String universityName, String e_Domain, boolean isActive) {
        this.universityName = universityName;
        this.e_Domain = e_Domain;
        this.isActive = isActive;
    }

    // Entity가 처음 저장되기 직전에 생성일을 자동으로 기록합니다.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
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
