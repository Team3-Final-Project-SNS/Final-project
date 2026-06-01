package com.example.team3final.domain.review.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.review.enums.ReviewBadTag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후기에서 선택한 아쉬운 점 태그입니다.
 *
 * 태그는 여러 개 선택할 수 있으며, 각 태그는 매너 점수 계산 시 -1점으로 반영됩니다.
 * REPORT_NEEDED 태그는 점수는 동일하게 -1점이지만 신고 흐름으로 이어질 수 있습니다.
 */
@Entity
@Getter
@Table(
        name = "review_bad_tags",
        indexes = {
                @Index(name = "idx_review_bad_tags_review", columnList = "review_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewBadTagEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_bad_tag_id")
    private Long id;

    /**
     * 태그가 연결된 후기 ID입니다.
     */
    @Column(name = "review_id", nullable = false, updatable = false)
    private Long reviewId;

    /**
     * 선택된 아쉬운 점 태그입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 30, updatable = false)
    private ReviewBadTag tag;

    /**
     * 태그가 반영하는 점수 변화량입니다.
     */
    @Column(name = "score_delta", nullable = false, updatable = false)
    private int scoreDelta;

    /**
     * 신고 흐름으로 연결할 수 있는 태그인지 여부입니다.
     */
    @Column(name = "reportable", nullable = false, updatable = false)
    private boolean reportable;

    @Builder
    private ReviewBadTagEntity(Long reviewId, ReviewBadTag tag) {
        this.reviewId = reviewId;
        this.tag = tag;
        this.scoreDelta = tag.getScoreDelta();
        this.reportable = tag.isReportable();
    }
}
