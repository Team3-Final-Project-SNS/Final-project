package com.example.team3final.domain.meet.repository;

import com.example.team3final.domain.meet.dto.response.MeetReminderResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetVerificationRepository extends JpaRepository<MeetVerification, Long> {

    Optional<MeetVerification> findByMatchId(Long matchId);

    // GPS 노쇼 판정용 PENDING 상태이면서 GPS 인증 가능 시간이 끝난 것들 조회
    List<MeetVerification> findAllByStatus(VerificationStatus status);

    // QR 노쇼 판정용 VERIFIED 상태이면서 QR 만료 시각이 지난 것들 조회
    List<MeetVerification> findAllByStatusAndQrExpiresAtBefore(VerificationStatus status, LocalDateTime now);

    // 노쇼 후보군 조회
    @Query("""
           SELECT mv
           FROM MeetVerification mv
           WHERE mv.status IN :statuses
           """)
    Page<MeetVerification> findAllByStatusIn(@Param("statuses")List<VerificationStatus> statuses, Pageable pageable);

    // 만료 처리가 필요한 연장 요청 조회
    List<MeetVerification> findAllByExtensionStatusAndExtensionRequestedAtBefore(
            ExtensionStatus status, LocalDateTime dateTime);

    // ==================== 만남 시간 알림용 쿼리 ====================

    // 30분 전 알림 미발송 조회 - MeetVerification + Match + Post JOIN으로 N+1 방지
    @Query("""
           SELECT new com.example.team3final.domain.meet.dto.response.MeetReminderResponseDto(
               mv.id, mv.matchId, p.authorId, m.applicantId)
           FROM MeetVerification mv
           JOIN Match m ON mv.matchId = m.id
           JOIN Post p ON m.postId = p.id
           WHERE m.status = 'MATCHED'
           AND mv.reminder30Sent = false
           AND p.meetAt BETWEEN :from AND :to
           """)
    List<MeetReminderResponseDto> findForReminder30(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // 15분 전 알림 미발송 조회
    @Query("""
           SELECT new com.example.team3final.domain.meet.dto.response.MeetReminderResponseDto(
               mv.id, mv.matchId, p.authorId, m.applicantId)
           FROM MeetVerification mv
           JOIN Match m ON mv.matchId = m.id
           JOIN Post p ON m.postId = p.id
           WHERE m.status = 'MATCHED'
           AND mv.reminder15Sent = false
           AND p.meetAt BETWEEN :from AND :to
           """)
    List<MeetReminderResponseDto> findForReminder15(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // 임박 알림 미발송 조회
    @Query("""
           SELECT new com.example.team3final.domain.meet.dto.response.MeetReminderResponseDto(
               mv.id, mv.matchId, p.authorId, m.applicantId)
           FROM MeetVerification mv
           JOIN Match m ON mv.matchId = m.id
           JOIN Post p ON m.postId = p.id
           WHERE m.status = 'MATCHED'
           AND mv.imminentSent = false
           AND p.meetAt BETWEEN :from AND :to
           """)
    List<MeetReminderResponseDto> findForImminent(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // NO_SHOW 상태이면서 noShowDecidedAt이 기준 시각 이전인 건 조회
    // 24시간 지난 노쇼 예정 건 찾기용
    List<MeetVerification> findAllByStatusInAndNoShowDecidedAtBefore(
            List<VerificationStatus> statuses,
            LocalDateTime deadline
    );
}
