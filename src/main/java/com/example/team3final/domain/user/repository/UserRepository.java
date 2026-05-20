package com.example.team3final.domain.user.repository;


import com.example.team3final.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 User Entity를 조회합니다.
    Optional<User> findByEmail(String email);

    // 회원가입 시 이메일로 사용자가 존재하는지 확인 (중복 가입 방지)
    boolean existsByEmail(String email);

    // 회원가입 시 닉네임 중복확인
    boolean existsByNickname(String nickname);
}
