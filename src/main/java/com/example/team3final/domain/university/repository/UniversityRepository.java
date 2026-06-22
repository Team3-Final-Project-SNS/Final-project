package com.example.team3final.domain.university.repository;

import com.example.team3final.domain.university.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {

    // 활성화된 대학 목록을 학교명 오름차순으로 조회합니다.
    List<University> findAllByIsActiveTrueOrderByUniversityNameAsc();

    // 회원가입 시 이메일 도메인으로 활성화된 학교가 존재하는지 확인
    boolean existsByeDomainAndIsActiveTrue(String eDomain);

    // OTP 검증 성공 후 학교 상세 정보 조회용 (단건 반환)
    Optional<University> findByeDomainAndIsActiveTrue(String eDomain);
}
