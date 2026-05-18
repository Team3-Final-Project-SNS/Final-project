package com.example.team3final.domain.user.repository;


import com.example.team3final.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 User Entity를 조회합니다.
    Optional<User> findByEmail(String email);
}
