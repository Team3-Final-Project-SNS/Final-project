package com.example.team3final.domain.admin.repository;

import com.example.team3final.domain.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    // 로그인 시 이메일로 관리자 조회
    Optional<Admin> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);
}
