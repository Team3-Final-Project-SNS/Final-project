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
        name = "user_locations",
        // 한 매칭에서 유저당 최신 위치 레코드 1개만 유지
        // 위치 이력 전체를 저장하지 않고, 대신 반경 진입/이탈 시각을 컬럼으로 관리
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_location_match_user",
                columnNames = {"match_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 위치가 연결된 matchId
    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    // 위치를 올린 유저 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 위도 - Post 엔티티와 동일한 타입 사용
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    // 경도
    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // precision = 10: 전체 숫자 자릿수 최대 10자리
    // scale = 7: 소수점 아래 7자리

    // 마지막 위치 업데이트 시각 - 1초마다 갱신
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 마지막 위치 업데이트 기준, 사용자가 약속 장소 반경 안에 있었는지 여부
    // QR 만료 시 "현재 자리에 있는지" 판단할 때 사용
    @Column(name = "is_in_range", nullable = false)
    private boolean isInRange;

    // 마지막으로 약속 장소 반경 안에 있었던 시각
    // 앱이 꺼져서 위치 업데이트가 끊긴 경우에도 마지막 체류 시각을 추적하기 위한 값
    @Column(name = "last_in_range_at")
    private LocalDateTime lastInRangeAt;

    // 반경 안에 있다가 밖으로 벗어난 시각
    // 최종 정책의 "둘 다 없으면 먼저 벗어난 사람만 노쇼" 판정에 사용
    @Column(name = "left_range_at")
    private LocalDateTime leftRangeAt;

    @Builder
    private UserLocation(Long matchId, Long userId, BigDecimal latitude, BigDecimal longitude, boolean isInRange) {
        LocalDateTime now = LocalDateTime.now();
        this.matchId = matchId;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = now;
        // 최초 저장 시점의 반경 안/밖 여부를 함께 저장
        this.isInRange = isInRange;
        // 최초 위치가 반경 안이면 lastInRangeAt을 기록
        this.lastInRangeAt = isInRange ? now : null;
        // 최초 위치가 반경 밖인 경우는 "안에 있다가 나간 것"이 아니므로 leftRangeAt은 null로 둠
        this.leftRangeAt = null;
    }

    // 위치 업데이트
    public void updateLocation(BigDecimal latitude, BigDecimal longitude, boolean newInRange) {
        LocalDateTime now = LocalDateTime.now();
        boolean wasInRange = this.isInRange;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = now;
        // 이번 위치가 반경 안이면 마지막 반경 안 체류 시각을 갱신
        // 또한 이전에 기록된 이탈 시각은 더 이상 현재 체류 구간의 이탈 시각이 아니므로 초기화
        if (newInRange) {
            this.lastInRangeAt = now;
            this.leftRangeAt = null;
        }
        // 직전까지 반경 안이었고, 이번 업데이트에서 반경 밖으로 바뀌었다면
        // 이 시각을 현재 체류 구간의 "이탈 시각"으로 기록
        if (wasInRange && !newInRange) {
            this.leftRangeAt = now;
        }
        // 현재 반경 안/밖 상태 갱신
        this.isInRange = newInRange;
    }
}
