package com.example.team3final.domain.meet.entity;

import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "meet_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Service - to - Service 방식 -> JPA 연관관계 매핑 없이 ID 값만 보관
    @Column(name = "match_id", nullable = false, updatable = false, unique = true)
    // updatable 한 번 연결된 매칭 ID는 변경 불가
    private Long matchId;

    // 등록자 GPS 50m 진입 시각
    @Column(name = "author_place_verified_at")
    private LocalDateTime authorPlaceVerifiedAt;

    // 신청자 GPS 50m 진입 시각
    @Column(name = "applicant_place_verified_at")
    private LocalDateTime applicantPlaceVerifiedAt;

    // QR 토큰 -> 등록자가 만남 QR을 요청할 때 서버가 발급하는 토큰 문자열
    // 초기 생성 시점엔 null → 장소 인증 완료 후 발급
    @Column(name = "qr_token", length = 255)
    private String qrToken;

    // 만남 인증 여부
    @Column(name = "is_meet_verified")
    private Boolean isMeetVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    // QR 토큰 만료 시각: 장소 인증 완료 시점 + 30분
    @Column(name = "qr_expires_at")
    private LocalDateTime qrExpiresAt;

    // 만남 인증 완료 시각
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 만남 시간 15분 연장 사용 여부 (성공 1회 한정)
    @Column(name = "is_extended", nullable = false)
    private boolean isExtended = false;

    // 연장된 최종 만남 시각 (수락 시 post.meetAt + 15분 저장)
    @Column(name = "extended_meet_at")
    private LocalDateTime extendedMeetAt;

    // 연장 요청 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "extension_status", nullable = false, length = 20)
    private ExtensionStatus extensionStatus = ExtensionStatus.NONE;

    // 연장 요청한 유저 ID (상대방 수락 권한 판단용)
    @Column(name = "extension_requester_id")
    private Long extensionRequesterId;

    // 연장 요청 시각 (5분 타임아웃 계산용)
    @Column(name = "extension_requested_at")
    private LocalDateTime extensionRequestedAt;

    // 노쇼 판정 시각
    @Column(name = "no_show_decided_at")
    private LocalDateTime noShowDecidedAt;

    // 30분 전 알림 발송 여부 (중복 발송 방지)
    @Column(name = "reminder30_sent", nullable = false)
    private boolean reminder30Sent = false;

    // 15분 전 알림 발송 여부 (중복 발송 방지)
    @Column(name = "reminder15_sent", nullable = false)
    private boolean reminder15Sent = false;

    // 만남 임박 알림 발송 여부 (중복 발송 방지)
    @Column(name = "imminent_sent", nullable = false)
    private boolean imminentSent = false;

    @Builder
    private MeetVerification(Long matchId, VerificationStatus status, Boolean isMeetVerified,
                             ExtensionStatus extensionStatus, boolean isExtended) {
        this.matchId = matchId;
        this.status = status;
        this.isMeetVerified = isMeetVerified;
        this.extensionStatus = extensionStatus;
        this.isExtended = isExtended;
    }

    public static MeetVerification createPending(Long matchId) {
        return MeetVerification.builder()
                .matchId(matchId)
                .status(VerificationStatus.PENDING) // 초기 상태 = PENDING 고정
                .isMeetVerified(false) // 초기 만남 인증 여부 = false
                .extensionStatus(ExtensionStatus.NONE)
                .isExtended(false)
                .build();
    }

    // 등록자 GPS 장소 인증 처리
    // 양쪽 모두 완료되면 자동으로 VERIFIED 상태로 전환
    public void verifyAuthorPlace() {
        this.authorPlaceVerifiedAt = LocalDateTime.now();
        updateToVerifiedIfDone();

    }

    // 신청자 GPS 장소 인증 처리
    public void verifyApplicantPlace() {
        this.applicantPlaceVerifiedAt = LocalDateTime.now();
        updateToVerifiedIfDone();
    }

    // 양 쪽 모두 GPS 인증이 완료됐을 때 VERIFIED로 상태 전환
    public void updateToVerifiedIfDone() {
        if (this.authorPlaceVerifiedAt != null && this.applicantPlaceVerifiedAt != null) {
            this.status = VerificationStatus.VERIFIED;
        }
    }

    // QR 토큰 발급
    public void issueQrToken(String qrToken, LocalDateTime qrExpiresAt) {
        this.qrToken = qrToken;
        this.qrExpiresAt = qrExpiresAt;
    }

    // QR 스캔으로 만남 인증 최종 완료 처리
    public void meetVerifiedDone() {
        this.status = VerificationStatus.DONE;
        this.isMeetVerified = true;
        this.completedAt = LocalDateTime.now();
    }

    // 등록자 GPS 인증 완료 여부
    public boolean isAuthorPlaceVerified() {
        return this.authorPlaceVerifiedAt != null;
    }

    // 신청자 GPS 인증 완료 여부
    public boolean isApplicantPlaceVerified() {
        return this.applicantPlaceVerifiedAt != null;
    }

    // QR 토큰이 현재 시각 기준으로 만료여부 확인
    public boolean isQrExpired() {
        // qrExpiresAt이 null이면 아직 발급 전이므로 만료 아님
        return this.qrExpiresAt != null && LocalDateTime.now().isAfter(this.qrExpiresAt);
    }

    // 등록자 노쇼 판정
    public void markAuthorNoShow() {
        this.status = VerificationStatus.HOST_NO_SHOW;
        this.isMeetVerified = false;
        this.noShowDecidedAt = LocalDateTime.now();
    }

    // 신청자 노쇼 판정
    public void markApplicantNoShow() {
        this.status = VerificationStatus.GUEST_NO_SHOW;
        this.isMeetVerified = false;
        this.noShowDecidedAt = LocalDateTime.now();
    }

    // 양측 노쇼
    public void markBothNoShow() {
        this.status = VerificationStatus.BOTH_NO_SHOW;
        this.isMeetVerified = false;
        this.noShowDecidedAt = LocalDateTime.now();
    }

    // 만남 시간 연장 관련 메서드
    // 연장 요청 처리
    public void requestExtension(Long requesterId) {
        this.extensionStatus = ExtensionStatus.REQUESTED;
        this.extensionRequesterId = requesterId;
        this.extensionRequestedAt = LocalDateTime.now();
    }

    // 연장 수락
    public void acceptExtension(LocalDateTime meetAt, long extensionMinutes) {
        this.extensionStatus = ExtensionStatus.ACCEPTED;
        this.isExtended = true;
        this.extendedMeetAt = meetAt.plusMinutes(extensionMinutes); // 원래 약속 시간에서 + 15분
    }

    // QR 만료 시각 15분 연장 (수락 시 함께 호출)
    public void extendQrExpiry(long extensionMinutes) {
        if (this.qrExpiresAt != null) {
            this.qrExpiresAt = this.qrExpiresAt.plusMinutes(extensionMinutes);
        }
    }

    // 연장 거절
    public void rejectExtension() {
        this.extensionStatus = ExtensionStatus.REJECTED;
    }

    // 연장 요청 만료 처리 (5분 타임아웃 시 스케줄러가 호출
    public void expireExtension() {
        this.extensionStatus = ExtensionStatus.EXPIRED;
    }

    // 연장 요청 만료 여부 확인
    public boolean isExtensionExpired() {
        return this.extensionRequestedAt != null && LocalDateTime.now().isAfter(this.extensionRequestedAt.plusMinutes(5));
    }

    // 30분 전 알림 발송 완료 처리
    public void markReminder30Sent() {
        this.reminder30Sent = true;
    }

    // 15분 전 알림 발송 완료 처리
    public void markReminder15Sent() {
        this.reminder15Sent = true;
    }

    // 임박 알림 발송 완료 처리
    public void markImminentSent() {
        this.imminentSent = true;
    }
}
