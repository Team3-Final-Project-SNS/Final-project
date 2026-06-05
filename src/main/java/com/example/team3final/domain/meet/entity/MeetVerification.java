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
    @Column(name = "qr_token")
    private String qrToken;

    // 만남 인증 여부
    @Column(name = "is_meet_verified")
    private Boolean isMeetVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    // 이의 제기 상태 체크를 위한 필드값
    @Enumerated(EnumType.STRING)
    @Column(name = "disputed_from_status", length = 20)
    private VerificationStatus disputedFromStatus;

    // QR 토큰 만료 시각: 장소 인증 완료 시점 + 30분
    @Column(name = "qr_expires_at")
    private LocalDateTime qrExpiresAt;

    // 만남 인증 완료 시각
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 만남 시간 10분 연장 사용 여부 (성공 1회 한정)
    @Column(name = "is_extended", nullable = false)
    private boolean isExtended = false;

    // 연장된 최종 만남 시각 (수락 시 post.meetAt + 10분 저장)
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

    // QR 토큰 저장
    // 발급 로직은 서비스(issueQrTokenIfNeeded)가 담당
    public void issueQrToken(String qrToken, LocalDateTime qrExpiresAt) {
        this.qrToken = qrToken;
        this.qrExpiresAt = qrExpiresAt;
    }

    // 만남 인증 최종 성공 처리 -> QR 스캔 성공 시 신청자가 호출
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

    // 노쇼 확정 처리 — 24시간 경과 후 judgeNoShowConfirmed 배치에서 호출
    public void confirmNoShow() {
        this.status = VerificationStatus.NO_SHOW_CONFIRMED;
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
        this.extendedMeetAt = meetAt.plusMinutes(extensionMinutes); // 원래 약속 시간에서 + 10분
    }

    // QR 만료 시각 10분 연장 (수락 시 함께 호출)
    public void extendQrExpiry(long extensionMinutes) {
        if (this.qrExpiresAt != null) {
            this.qrExpiresAt = this.qrExpiresAt.plusMinutes(extensionMinutes);
        }
    }

    // 연장 요청 중인지 확인 (스케줄러 중복 처리 방지용)
    public boolean isExtensionRequested() {
        return this.extensionStatus == ExtensionStatus.REQUESTED;
    }

    // 연장 거절
    public void rejectExtension() {
        this.extensionStatus = ExtensionStatus.REJECTED;
    }

    // 연장 요청 만료 처리 (5분 타임아웃 시 스케줄러가 호출
    public void expireExtension() {
        this.extensionStatus = ExtensionStatus.EXPIRED;
    }

    // 연장 요청이 5분 타임아웃됐는지 실시간 확인
    // 스케줄러가 EXPIRED로 바꾸기 전에 수락/거절 요청이 들어오면
    // 이 메서드로 먼저 만료 여부를 체크해서 즉시 EXPIRED 처리함
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

    /**
     * [이의제기 접수] 노쇼 예정 상태에서 이의제기 접수 시 호출
     * - 현재 노쇼 상태를 disputedFromStatus에 백업하고 DISPUTE로 전환
     * - 이후 판정 결과에 따라 아래 3개 메소드 중 하나로 처리됨:
     *   REJECTED          → rejectDispute()
     *   PARTIALLY_ACCEPTED → partiallyAcceptDispute()
     *   ACCEPTED          → completeByDispute()
     */
    public void markDispute() {
        // 노쇼 예정 상태(HOST/GUEST/BOTH_NO_SHOW)가 아니면 이의제기 불가
        if (this.status != VerificationStatus.HOST_NO_SHOW
                && this.status != VerificationStatus.GUEST_NO_SHOW
                && this.status != VerificationStatus.BOTH_NO_SHOW) {
            throw new IllegalStateException("노쇼 예정 상태에서만 이의제기 상태로 전환할 수 있습니다.");
        }

        // 원래 노쇼 상태를 백업 (판정 후 복원 또는 참조용)
        this.disputedFromStatus = this.status;
        // 이의제기 검토 중 상태로 전환
        this.status = VerificationStatus.DISPUTE;
    }

    /**
     * [기각 - REJECTED] 이의제기 기각 시 호출
     * - 정책: 정당한 사유 없음 → 노쇼 확정 → 예치 포인트 전액 몰수
     * - 백업해둔 원래 노쇼 상태(HOST/GUEST/BOTH_NO_SHOW)를 복원
     * - 반환된 상태값을 AdminDisputeService에서 받아 포인트 몰수 처리에 사용
     */
    public VerificationStatus rejectDispute() {
        // DISPUTE 상태이고 백업된 노쇼 상태가 있을 때만 복원 가능
        if (this.status == VerificationStatus.DISPUTE && this.disputedFromStatus != null) {
            // 원래 노쇼 상태로 복원 (HOST_NO_SHOW / GUEST_NO_SHOW / BOTH_NO_SHOW)
            this.status = this.disputedFromStatus;
            // 백업 필드 초기화
            this.disputedFromStatus = null;
        }
        // 복원된 노쇼 상태를 반환 → 호출자(AdminDisputeService)가 포인트 몰수 처리에 사용
        return this.status;
    }

    /**
     * [부분 수용 - PARTIALLY_ACCEPTED] 이의제기 부분 수용 시 호출
     * - 정책: 응급실/장례식 등 불가피한 사유로 실제 만남이 이루어지지 않은 경우
     * - 노쇼가 아닌 매칭 취소로 처리 → 예치 포인트 50% 반환
     * - "만남은 없었지만 정상적인 취소 사유가 인정된" 케이스
     */
    public void partiallyAcceptDispute() {
        // DISPUTE 상태에서만 호출 가능
        if (this.status != VerificationStatus.DISPUTE) {
            throw new IllegalStateException("이의제기 검토 중 상태에서만 부분 수용 처리할 수 있습니다.");
        }

        // 노쇼가 아닌 취소로 상태 전환 (포인트 50% 반환은 Service에서 처리)
        this.status = VerificationStatus.NO_SHOW_CANCELLED;
        // 백업 필드 초기화
        this.disputedFromStatus = null;
    }

    /**
     * [수용 - ACCEPTED] 이의제기 수용 시 호출
     * - 정책: 실제 만남은 있었으나 서비스 오류(GPS/QR 인식 오류 등)로 인증만 실패한 경우
     * - 만남 완료(DONE)로 처리 → 예치 포인트 100% 반환
     * - "만남은 있었지만 시스템 문제로 인증이 안된" 케이스
     */
    public void completeByDispute() {
        // DISPUTE 상태에서만 호출 가능
        if (this.status != VerificationStatus.DISPUTE) {
            throw new IllegalStateException("이의제기 검토 중 상태에서만 수용 처리할 수 있습니다.");
        }

        // 정상 만남 완료 상태로 전환
        this.status = VerificationStatus.DONE;
        // 만남 인증 완료 처리 (실제 만남이 있었음을 인정)
        this.isMeetVerified = true;
        // 완료 시각 기록
        this.completedAt = LocalDateTime.now();
        // 백업 필드 초기화
        this.disputedFromStatus = null;
    }
}
