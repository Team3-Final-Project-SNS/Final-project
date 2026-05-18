package com.example.team3final.domain.match.entity;

import com.example.team3final.common.entity.BaseEntity;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.post.entity.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "matches") // MySQL/SQL 표준 예약어 회피
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id", unique = true, updatable = false)
    private Long id;

    // 매칭된 게시글 (1:1, post_id UNIQUE)
    @Column(name = "post_id",nullable = false)
    private Long postId;

    // 신청자 (등록자는 post.author를 통해 참조)
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    // 신청자 예치 포인트 (등록자 예치 포인트는 post.authorDeposit 참조)
    @Column(name = "applicant_deposit", nullable = false)
    private int applicantDeposit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MatchStatus status;

    // 매칭 확정 시각
    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    // 만남 완료 시각 (QR 인증 완료 시점)
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private Match(Long postId, Long applicantId, int applicantDeposit) {
        this.postId = postId;
        this.applicantId = applicantId;
        this.applicantDeposit = applicantDeposit;
        this.status = MatchStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
    }

    // ===== 비즈니스 메서드 =====

    // 만남 정상 완료 (QR 인증까지 완료)
    public void complete() {
        this.status = MatchStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    // 매칭 취소 (약속 시간 이전)
    public void cancel() {
        this.status = MatchStatus.CANCELLED;
    }

    // 노쇼 판정 (장소/QR 인증 단계별 결과)
    public void markNoShow(MatchStatus noShowStatus) {
        if (noShowStatus != MatchStatus.AUTHOR_NO_SHOW
        && noShowStatus != MatchStatus.APPLICANT_NO_SHOW
        && noShowStatus != MatchStatus.BOTH_NO_SHOW) {
            throw new IllegalArgumentException("노쇼 상태가 아닙니다:" + noShowStatus);
        }
        this.status = noShowStatus;
    }

    // 이의 제기 상태로 전환
    public void dispute() {
        this.status = MatchStatus.DISPUTED;
    }

    // ===== 조회 메서드 =====

    // 매칭 양측 당사자(등록자 / 신청자)인지 검증
    public boolean isParticipant(Long userId, Long authorId) {
        return authorId.equals(userId) || this.applicantId.equals(userId);
    }

    public boolean isApplicant(Long userId) {
        return this.applicantId.equals(userId);
    }
}
