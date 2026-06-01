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
 * 후기를 받은 대상자는 matchId와 writerId를 기준으로 매칭 참여자 중 반대편 유저를
 * 서비스 계층에서 계산합니다. 실제 작성 가능 여부, 7일 제한, 중복 작성 검증은
 * 서비스 계층에서 처리합니다.
 */
@Entity
@Getter
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reviews_match_writer",
                        columnNames = {"match_id", "writer_id"}
                )
        },
        indexes = {
                @Index(name = "idx_reviews_match_writer", columnList = "match_id, writer_id"),
                @Index(name = "idx_reviews_writer_created", columnList = "writer_id, created_at")
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
     * 선택 태그로 계산한 점수 변화량입니다.
     *
     * 긍정 태그는 각 +1점, 아쉬운 점 태그는 각 -1점으로 계산합니다.
     */
    @Column(name = "tag_score_delta", nullable = false, updatable = false)
    private int tagScoreDelta;

    @Builder
    private Review(
            Long matchId,
            Long writerId,
            int tagScoreDelta
    ) {
        this.matchId = matchId;
        this.writerId = writerId;
        this.tagScoreDelta = tagScoreDelta;
    }
}
