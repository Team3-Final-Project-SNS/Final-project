package com.example.team3final.domain.user.repository;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

    // 관리자 사용자 목록 조회
    // 기존 UserRepository의 findAllByForAdmin 메서드명과 반환 타입을 유지
    // 서비스 계층에서는 기존처럼 Page<User>를 받아 PageResponseDto로 감싸므로,
    // QueryDSL로 변경해도 서비스 코드는 수정하지 않는다.
    Page<User> findAllByForAdmin(UserStatus status, String keyword, Pageable pageable);
}
