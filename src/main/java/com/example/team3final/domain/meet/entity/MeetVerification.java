package com.example.team3final.domain.meet.entity;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
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

    // 등록자 GPS 서버 판정 반경 60m 진입 시각
    @Column(name = "author_place_verified_at")
    private LocalDateTime authorPlaceVerifiedAt;

    // 신청자 GPS 서버 판정 반경 60m 진입 시각
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

    // QR 토큰 만료 시각: QR 발급 시점 + 10분
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

    // 노쇼 예정 알림 발송 여부 (중복 발송 방지)
    @Column(name = "no_show_warning_sent", nullable = false)
    private boolean noShowWarningSent = false;

    // 노쇼 확정 알림 발송 여부 (중복 발송 방지)
    @Column(name = "no_show_confirmed_sent", nullable = false)
    private boolean noShowConfirmedSent = false;

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

    // QR 인증 완료 상태 또는 완료 플래그가 남아 있으면 정상 만남 완료로 판단한다.
    // 상태와 플래그 중 하나만 정상이어도 노쇼 판정과 알림에서 제외하기 위한 이중 방어다.
    public boolean isMeetAlreadyCompleted() {
        return this.status == VerificationStatus.DONE
                || Boolean.TRUE.equals(this.isMeetVerified);
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
    // 그룹 연장은 같은 Post의 모든 활성 MeetVerification에 전파
    // 따라서 호출된 모든 MV에 동일한 extendedMeetAt을 세팅
    public void acceptExtension(LocalDateTime meetAt, long extensionMinutes) {
        this.extensionStatus = ExtensionStatus.ACCEPTED;
        this.isExtended = true;
        this.extendedMeetAt = meetAt.plusMinutes(extensionMinutes);
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
    public boolean isExtensionExpired(long timeoutMinutes) {
        return this.extensionRequestedAt != null
                && LocalDateTime.now().isAfter(this.extensionRequestedAt.plusMinutes(timeoutMinutes));
    }

    /**
     * [이의제기 접수] 노쇼 예정 상태에서 이의제기 접수 시 호출
     * - 현재 노쇼 상태를 disputedFromStatus에 백업하고 DISPUTE로 전환
     * - 이후 판정 결과에 따라 처리됨:
     *   ACCEPTED           → completeByDispute()
     *   PARTIALLY_ACCEPTED → confirmNoShowByAdmin()
     *   REJECTED           → rejectDispute() 후 confirmNoShowByAdmin()
     */
    public void markDispute() {
        // 1. 이미 이의제기가 제출된 상태인지 먼저 검사
        if (this.status == VerificationStatus.DISPUTE) {
            // 이미 이의제기 상태로 전환된 매칭
            throw new DisputeException(ErrorCode.DISPUTE_ALREADY_SUBMITTED);
        }

        // 2. 노쇼 예정 상태(HOST/GUEST/BOTH_NO_SHOW)가 아니면 이의제기 불가
        if (this.status != VerificationStatus.HOST_NO_SHOW
                && this.status != VerificationStatus.GUEST_NO_SHOW
                && this.status != VerificationStatus.BOTH_NO_SHOW) {
            // 노쇼 예정 상태만 이의제기 제출 대상
            throw new DisputeException(ErrorCode.DISPUTE_NOT_NO_SHOW);
        }

        // 3. 원래 노쇼 상태를 백업 및 상태 전환
        this.disputedFromStatus = this.status;
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
     * disputedFromStatus(원래 노쇼 상태)만 꺼내서 반환
     * PARTIALLY_ACCEPTED 판정 시 사용
     * - rejectDispute()와 달리 status를 복원하지 않음
     * - partiallyAcceptDispute()와 함께 사용할 때 순서 의존성 없음
     */
    public VerificationStatus getDisputedFromStatus() {
        if (this.disputedFromStatus == null) {
            // 이의제기 판정 복원에 필요한 기존 노쇼 상태 없음
            throw new MeetException(ErrorCode.MEET_DISPUTE_BACKUP_NOT_FOUND);
        }
        return this.disputedFromStatus;
    }

    /**
     * [수용 - ACCEPTED] 이의제기 수용 시 호출
     * - 정책: 실제 만남은 있었으나 서비스 오류(GPS/QR 인식 오류 등)로 인증만 실패한 경우
     * - 만남 완료(DONE)로 처리 → 예치 포인트 100% 반환
     * - "만남은 있었지만 시스템 문제로 인증이 안된" 케이스
     */
    public void completeByDispute() {
        // DISPUTE 상태가 정상 경로지만, 기존/테스트 데이터가 노쇼 예정 상태에 남아 있어도 수용 판정은 만남 완료로 복구한다.
        if (this.status != VerificationStatus.DISPUTE
                && this.status != VerificationStatus.HOST_NO_SHOW
                && this.status != VerificationStatus.GUEST_NO_SHOW
                && this.status != VerificationStatus.BOTH_NO_SHOW) {
            // DISPUTE 또는 노쇼 예정 상태만 이의제기 수용 처리 대상
            throw new MeetException(ErrorCode.MEET_DISPUTE_INVALID_STATUS);
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

    // 노쇼 예정 알림 발송 완료 처리
    public void markNoShowWarningSent() {
        this.noShowWarningSent = true;
    }

    // 노쇼 확정 알림 발송 완료 처리
    public void markNoShowConfirmedSent() {
        this.noShowConfirmedSent = true;
    }


    // 관리자 판정으로 노쇼가 최종 확정된 경우 호출
    // 실제 만남이 없었다면 노쇼 자체는 맞음, 다만 관리자 판정값에 따라 포인트 반환 비율만 달라짐
    public void confirmNoShowByAdmin() {
        this.status = VerificationStatus.NO_SHOW_CONFIRMED;
        this.isMeetVerified = false;
        this.disputedFromStatus = null;
    }
}
