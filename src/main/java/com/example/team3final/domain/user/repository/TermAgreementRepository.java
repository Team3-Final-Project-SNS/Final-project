package com.example.team3final.domain.user.repository;

import com.example.team3final.domain.user.entity.TermAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermAgreementRepository extends JpaRepository<TermAgreement, Long> {
    // 현재는 저장만 필요 — 기본 save() 메서드를 JpaRepository에서 상속
}
