package com.example.team3final.domain.point.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Getter
@Entity
@Table(name = "points") // 사용자별 현재 포인트 잔액을 저장하는 테이블입니다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // 포인트 ID입니다.
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true) // 한 사용자는 하나의 포인트 계좌만 가집니다.
    private Long userId;

    @Column(name = "balance", nullable = false) // 현재 포인트 잔액입니다.
    private int balance;

    @Column(name = "created_at", nullable = false, updatable = false) // 포인트 계좌 생성 시각입니다.
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false) // 포인트 잔액이 마지막으로 변경된 시각입니다.
    private LocalDateTime updatedAt;

    @Builder
    private Point(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    // Entity가 처음 저장되기 직전에 생성일과 수정일을 기록합니다.
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Entity가 수정되기 직전에 수정일을 갱신합니다.
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 포인트를 증가시킵니다.
    public void add(int amount) {
        this.balance += amount;
    }

    // 포인트를 차감합니다.
    public void subtract(int amount) {
        this.balance -= amount;
    }

    // 포인트 잔액이 충분한지 확인합니다.
    public boolean hasEnoughBalance(int amount) {
        return this.balance >= amount;
    }
}