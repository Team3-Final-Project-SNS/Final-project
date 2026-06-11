package com.example.team3final.domain.meet.repository;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // NO_SHOW 상태이면서 noShowDecidedAt이 기준 시각 이전인 건 조회
    // 24시간 지난 노쇼 예정 건 찾기용
    List<MeetVerification> findAllByStatusInAndNoShowDecidedAtBefore(
            List<VerificationStatus> statuses,
            LocalDateTime deadline
    );

    // matchId 목록으로 MeetVerification 벌크 조회
    // getMeetVerification()에서 형제 MV 전체를 한 번에 가져올 때 사용 (N+1 방지)
    List<MeetVerification> findAllByMatchIdIn(List<Long> matchIds);

    // matchId 목록에 해당하는 MeetVerification들을 PESSIMISTIC_WRITE 락으로 조회
    // 같은 Post의 그룹 연장 요청이 동시에 들어왔을 때 상태 덮어쓰기 방지를 위해 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                SELECT mv
                FROM MeetVerification mv
                WHERE mv.matchId IN :matchIds
                ORDER BY mv.id ASC
            """)
    List<MeetVerification> findAllByMatchIdInWithLock(@Param("matchIds") List<Long> matchIds);

    // 사용자 노쇼 예정 매칭 목록 조회 — matchId 목록 + 노쇼 상태 필터
    List<MeetVerification> findAllByMatchIdInAndStatusIn(
            List<Long> matchIds,
            List<VerificationStatus> statuses
    );
}
