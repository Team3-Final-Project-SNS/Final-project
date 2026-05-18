package com.example.team3final.domain.meet.repository;

import com.example.team3final.domain.meet.entity.MeetVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetVerificationRepository extends JpaRepository<MeetVerification, Long> {

    Optional<MeetVerification> findByMatchId(Long matchId);
}
