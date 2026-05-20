package com.example.team3final.domain.meet.repository;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetVerificationRepository extends JpaRepository<MeetVerification, Long> {

    Optional<MeetVerification> findByMatchId(Long matchId);

    // GPS 노쇼 판정용 PENDING 상태이면서 GPS 인증 가능 시간이 끝난 것들 조회
    List<MeetVerification> findAllByStatus(VerificationStatus status);

    // QR 노쇼 판정용 VERIFIED 상태이면서 QR 만료 시각이 지난 것들 조회
    List<MeetVerification> findAllByStatusAndQrExpiresAtBefore(VerificationStatus status, LocalDateTime now);
}
