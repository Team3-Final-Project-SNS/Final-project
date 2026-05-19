package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.entity.User;

public interface UserService {

    // 이메일로 사용자 ID를 조회합니다.
    // 다른 도메인에서 로그인 사용자의 userId가 필요할 때 사용합니다.
    Long getUserIdByEmail(String email);

    // 회원가입 시 가입되어있는 이메일인지 검증
    boolean isEmailAlreadyRegistered(String email);

    User findByEmail(String email);
}
