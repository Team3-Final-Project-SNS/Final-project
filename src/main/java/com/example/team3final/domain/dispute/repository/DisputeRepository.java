package com.example.team3final.domain.dispute.repository;

import com.example.team3final.domain.dispute.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    // 같은 매칭에 대해 같은 유저가 이미 이의제기를 냈는지 검사.
    boolean existsByMatchIdAndSubmitterId(Long matchId, Long submitterId);

    // 특정 매칭에 대해 특정 유저가 제출한 이의제기 1건 조회.
    Optional<Dispute> findByMatchIdAndSubmitterId(Long matchId, Long submitterId);
}
