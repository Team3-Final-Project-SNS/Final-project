package com.example.team3final.domain.user.repository;


import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 User Entity를 조회합니다.
    Optional<User> findByEmail(String email);

    // 회원가입 시 이메일로 사용자가 존재하는지 확인 (중복 가입 방지)
    boolean existsByEmail(String email);

    // 회원가입 시 닉네임 중복확인
    boolean existsByNickname(String nickname);

    // Admin 유저 목록 조회
    @Query("""
        SELECT u
        FROM User u
        WHERE (:status IS NULL OR u.status = :status)
        AND (:keyword IS NULL OR u.name LIKE %:keyword% OR u.nickname LIKE %:keyword%)
        """)
    Page<User> findAllByForAdmin(@Param("status") UserStatus status, @Param("keyword") String keyword, Pageable pageable);

    // 같은 학교(universityId)에 속한 활성 유저(ACTIVE) ID 목록 조회
    // - ACTIVE 조건: 탈퇴(WITHDRAWN), 정지(SUSPENDED) 유저는 게시글 목록에서 제외
    // - @SQLRestriction("deleted_at IS NULL")이 자동으로 적용되므로
    //   soft delete된 유저도 자동 제외됨
    @Query("SELECT u.id FROM User u WHERE u.universityId = :universityId AND u.status = 'ACTIVE'")
    List<Long> findIdsByUniversityId(@Param("universityId") Long universityId);







    // 일단 ai db 활용을 위해서 임시로. 나중에 리팩토링할때 서비스 to 서비스로 변경 예정.
    @Query("""
    SELECT u.id
    FROM User u
    WHERE u.universityId = :universityId
    AND u.status = com.example.team3final.domain.user.enums.UserStatus.ACTIVE
    """)
    List<Long> findActiveUserIdsByUniversityId(@Param("universityId") Long universityId);
}
