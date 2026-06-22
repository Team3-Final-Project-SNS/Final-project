package com.example.team3final.domain.review.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다시 만나고 싶지 않아요 선택으로 만들어지는 사용자 회피 관계입니다.
 *
 * 리뷰 작성 시 한쪽이 선택하더라도 이후 노출/매칭 차단은 양방향으로 적용되어야 하므로,
 * 서비스 계층에서 A -> B, B -> A 관계를 각각 저장합니다.
 */
@Entity
@Getter
@Table(
        name = "user_avoid_relations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_avoid_relations_user_avoided",
                        columnNames = {"user_id", "avoided_user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvoidRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_avoid_relation_id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "avoided_user_id", nullable = false, updatable = false)
    private Long avoidedUserId;

    @Column(name = "review_id", nullable = false, updatable = false)
    private Long reviewId;

    @Builder
    private UserAvoidRelation(Long userId, Long avoidedUserId, Long reviewId) {
        this.userId = userId;
        this.avoidedUserId = avoidedUserId;
        this.reviewId = reviewId;
    }
}
