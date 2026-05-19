package com.example.team3final.domain.location.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_location",
        // DB 레벨에서 match_id + user_id 조합 중복 방지
        // 한 매칭에서 유저당 위치 레코드 1개만 존재하도록 보장
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_USER_LOCATION_MATCH_USER",
                columnNames = {"match_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 위도 - Post 엔티티와 동일한 타입 사용
    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    // 경도
    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    // 마지막 위치 업데이트 시각 - 1초마다 갱신
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserLocation(Long matchId, Long userId, BigDecimal latitude, BigDecimal longitude) {
        this.matchId = matchId;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = LocalDateTime.now();
    }

    // 위치 업데이트
    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = LocalDateTime.now();
    }
}
