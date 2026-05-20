package com.example.team3final.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "term_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 약관에 동의한 유저 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 동의한 약관의 버전
    @Column(name = "term_version", nullable = false,length = 20)
    private String termVersion;

    // 약관에 동의한 시각
    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    @Builder
    private TermAgreement(Long userId, String termVersion) {
        this.userId = userId;
        this.termVersion = termVersion;
        this.agreedAt = LocalDateTime.now(); // 저장 시점을 동의 시각으로 기록
    }
}
