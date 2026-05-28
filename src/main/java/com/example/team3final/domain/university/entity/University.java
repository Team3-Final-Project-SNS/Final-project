package com.example.team3final.domain.university.entity;


import com.example.team3final.common.entity.BaseTimeEntity;
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
public class University extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // ERD: ID
    private Long id;

    @Column(name = "university_name", nullable = false, length = 100) // ERD: 학교명
    private String universityName;

    @Column(name = "e_domain", nullable = false, unique = true, length = 100) // ERD: 이메일 도메인
    private String eDomain;

    @Column(name = "is_active", nullable = false) // ERD: 활성 여부
    private boolean isActive;

    @Column(name = "deactivated_at") // ERD: 비활성화일
    private LocalDateTime deactivatedAt;

    @Builder
    private University(String universityName, String eDomain, boolean isActive) {
        this.universityName = universityName;
        this.eDomain = eDomain;
        this.isActive = isActive;
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
