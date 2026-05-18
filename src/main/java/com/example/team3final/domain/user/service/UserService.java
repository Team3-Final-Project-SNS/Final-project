package com.example.team3final.domain.user.service;

public interface UserService {

    // 이메일로 사용자 ID를 조회합니다.
    // 다른 도메인에서 로그인 사용자의 userId가 필요할 때 사용합니다.
    Long getUserIdByEmail(String email);
}
