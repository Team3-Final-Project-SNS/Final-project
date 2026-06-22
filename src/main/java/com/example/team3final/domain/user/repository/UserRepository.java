package com.example.team3final.domain.user.repository;


import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    // AI_report 관리자 콘솔 챗봇의 대시보드 요약용 읽기 전용 집계입니다.
    long countByStatus(UserStatus status);

    // 이메일로 User Entity를 조회합니다.
    Optional<User> findByEmail(String email);

    // 회원가입 시 이메일로 사용자가 존재하는지 확인 (중복 가입 방지)
    boolean existsByEmail(String email);

    // 회원가입 시 닉네임 중복확인
    boolean existsByNickname(String nickname);

    // 특정 상태를 제외하고 이메일 존재 여부 확인 (탈퇴한 이메일은 false 반환 -> 재가입 허용)
    boolean existsByEmailAndStatusNot(String email, UserStatus status);

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

    // 닉네임 LIKE 검색 -> 관리자 게시글 작성자 검색용
    @Query("SELECT u.id FROM User u WHERE u.nickname LIKE %:nickname%")
    List<Long> findIdsByNicknameLike(@Param("nickname") String nickname);

    // 비관적락 관련 메서드
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithPessimisticLock(@Param("id") Long id);
}
