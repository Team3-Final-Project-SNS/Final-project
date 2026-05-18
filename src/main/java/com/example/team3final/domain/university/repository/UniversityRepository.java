package com.example.team3final.domain.university.repository;

import com.example.team3final.domain.university.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniversityRepository extends JpaRepository<University, Long> {

    // 활성화된 대학 목록을 학교명 오름차순으로 조회합니다.
    List<University> findAllByIsActiveTrueOrderByUniversityNameAsc();
}
