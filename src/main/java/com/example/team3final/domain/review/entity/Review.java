package com.example.team3final.domain.review.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 완료 후 작성되는 사용자 후기입니다.
 *
 * 한 매칭에서 작성자와 대상자를 분리해 저장하므로, 등록자와 신청자 모두
 * 서로에게 후기를 작성할 수 있습니다. 실제 작성 가능 여부, 7일 제한,
 * 중복 작성 검증은 서비스 계층에서 처리합니다.
 */
@Entity
@Getter
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_reviews_match_writer", columnList = "match_id, writer_id"),
                @Index(name = "idx_reviews_target_created", columnList = "target_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    public static final int REVIEW_REWARD_POINT = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    /**
     * 후기가 작성된 매칭 ID입니다.
     */
    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    /**
     * 후기를 작성한 사용자 ID입니다.
     */
    @Column(name = "writer_id", nullable = false, updatable = false)
    private Long writerId;

    /**
     * 후기를 받은 사용자 ID입니다.
     */
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /**
     * 선택 후기 내용입니다.
     */
    @Column(name = "content", length = 200, updatable = false)
    private String content;

    /**
     * 선택 태그로 계산한 점수 변화량입니다.
     *
     * 긍정 태그는 각 +1점, 아쉬운 점 태그는 각 -1점으로 계산합니다.
     */
    @Column(name = "tag_score_delta", nullable = false, updatable = false)
    private int tagScoreDelta;

    /**
     * 후기 작성 보상 포인트 지급 여부입니다.
     */
    @Column(name = "reward_point_paid", nullable = false)
    private boolean rewardPointPaid;

    /**
     * 후기 작성으로 지급되는 보상 포인트입니다.
     */
    @Column(name = "reward_point", nullable = false, updatable = false)
    private int rewardPoint;

    @Builder
    private Review(
            Long matchId,
            Long writerId,
            Long targetId,
            String content,
            int tagScoreDelta
    ) {
        this.matchId = matchId;
        this.writerId = writerId;
        this.targetId = targetId;
        this.content = content;
        this.tagScoreDelta = tagScoreDelta;
        this.rewardPointPaid = false;
        this.rewardPoint = REVIEW_REWARD_POINT;
    }

    /**
     * 후기 작성 보상 지급 완료 상태로 변경합니다.
     */
    public void markRewardPointPaid() {
        this.rewardPointPaid = true;
    }
}
