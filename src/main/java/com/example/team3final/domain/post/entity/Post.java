package com.example.team3final.domain.post.entity;

import com.example.team3final.common.entity.SoftDeleteEntity;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.enums.PostStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE posts SET deleted_at = NOW() WHERE post_id = ?")
public class Post extends SoftDeleteEntity {

        // 최소 책임비 포인트
        public static  final int MIN_AUTHOR_DEPOSIT = 200;

        // 책임비 포인트 단위 (100P 단위)
        public static final int DEPOSIT_UNIT = 100;

        // 게시글 목록 조회 시 페이지당 최대 항목 수
        public static final int MAX_PAGE_SIZE = 50;

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "post_id")
        private Long id;

        @Column(name = "author_id", nullable = false)
        private Long authorId;

        // 만남 희망 시간
        @Column(name = "meet_at", nullable = false)
        private LocalDateTime meetAt;

        // 만남 장소명 (정문, 후문, 기숙사 등)
        @Column(name = "place_name", nullable = false, length = 200)
        private String placeName;

        // 약속 장소 위도 (-90 ~ 90)
        @Column(name = "place_lat", nullable = false, precision = 10, scale = 7)
        private BigDecimal placeLat;

        // 약속 장소 경도 (-180 ~ 180)
        @Column(name = "place_lng", nullable = false, precision = 10, scale = 7)
        private BigDecimal placeLng;

        // 한마디 (자유 텍스트, 선택)
        @Column(name = "content", columnDefinition = "TEXT")
        private String content;

        // 등록자 책임비 포인트 (예치 포인트)
        @Column(name = "author_deposit", nullable = false)
        private int authorDeposit;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        private PostStatus status;

        // 최대 참여 인원 (등록자 포함)
        @Column(name = "max_applicants", nullable = false)
        private int maxApplicants;

        // 현재 참여 인원
        @Column(name = "current_applicants", nullable = false)
        private int currentApplicants;

        @Builder
        private Post(Long authorId, LocalDateTime meetAt, String placeName,
                     BigDecimal placeLat, BigDecimal placeLng, String content,
                     int authorDeposit, int maxApplicants) {
                this.authorId = authorId;
                this.meetAt = meetAt;
                this.placeName = placeName;
                this.placeLat = placeLat;
                this.placeLng = placeLng;
                this.content = content;
                this.authorDeposit = authorDeposit;
                this.maxApplicants = maxApplicants > 0 ? maxApplicants : 1; // 기본값 1 (1:1 매칭)
                this.currentApplicants = 1;
                this.status = PostStatus.OPEN;
        }

        // ===== 비즈니스 메서드 =====

        // 게시글 수정 (OPEN 상태에서마 가능 - 호출 측에서 상태 검증 후 호출)
        // authorDeposit 차액 처리는 호출 측(Service)에서 PointTransaction과 함께 처리.
        public void update(LocalDateTime meetAt, String placeName,
                           BigDecimal placeLat, BigDecimal placeLng,
                           String content, Integer authorDeposit) {
                if (meetAt != null) this.meetAt = meetAt;
                if (placeName != null) this.placeName = placeName;
                if (placeLat != null) this.placeLat = placeLat;
                if (placeLng != null) this.placeLng = placeLng;
                if (content != null) this.content = content;
                if (authorDeposit != null) this.authorDeposit = authorDeposit;
        }

        // 매칭 확정 시 상태 전이
        public void match() {
                this.status = PostStatus.MATCHED;
        }

        // 게시글 만료 처리 — meetAt이 지났고 OPEN 상태일 때 스케줄러가 호출
        // OPEN → EXPIRED 전이만 허용 (다른 상태에서 호출되면 무시해도 되지만 명시적 방어)
        public void expire() {
                // isOpen() = this.status == PostStatus.OPEN
                // OPEN이 아닌 게시글은 만료 처리 대상이 아님
                if (!isOpen()) {
                        throw new PostException(ErrorCode.POST_NOT_OPEN);
                }
                this.status = PostStatus.EXPIRED;
        }

        // 만남 정상 완료
        public void complete() {
                // 상태 전이 규칙 검증: MATCHED가 아니면 완료 처리 불가
                if (!isMatched()) {
                        throw new PostException(ErrorCode.POST_NOT_MATCHED);
                }
                this.status = PostStatus.COMPLETED;
        }

        // 게시글 취소 (작성자 삭제 / 매칭 취소)
        public void cancel() {
                this.status = PostStatus.CANCELLED;
        }

        // 매칭 취소 시 OPEN으로 복구 — MATCHED 상태에서 신청자 취소 등으로 정원이 빈 경우
        public void reopen() {
                if (this.status != PostStatus.MATCHED) {
                        throw new PostException(ErrorCode.POST_NOT_MATCHED);
                }
                this.status = PostStatus.OPEN;
        }

        // 소프트 삭제 — 실제 행 삭제 대신 deleted_at 만 찍음
        public void delete() {
                super.delete();
        }

        // ===== 조회 메서드 =====

        public boolean isOpen() {
                return this.status == PostStatus.OPEN;
        }

        // 매칭 완료 상태 여부 검증 (complete() 전이 가능 여부 체크용)
        public boolean isMatched() {
                return this.status == PostStatus.MATCHED;
        }

        // 본인 게시글 여부 검증 (수정/삭제 권한 체크용)
        public boolean isAuthor(Long userId) {
                return this.authorId.equals(userId);
        }

        // 참여 인원 증가
        public void increaseCurrentApplicants() {
                this.currentApplicants++;
        }

        // 모집 완료 여부 확인
        public boolean isFull() {
                return this.currentApplicants >= this.maxApplicants;
        }

        // 참여 인원 감소 (매칭 취소 시)
        public void decreaseCurrentApplicants() {
                if (this.currentApplicants > 0) {
                        this.currentApplicants--;
                }
        }
}
