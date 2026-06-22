package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Getter
public class UserDetailsImpl implements UserDetails {

    private final Long userId;          // JWT에서 꺼낼 userId
    private final String email;
    private final String password;
    private final UserStatus status;   // 계정 활성 상태
    private final LocalDateTime suspendedUntil;  // 정지 만료 시각

    // User 엔티티 -> UserDetailsImpl 변환
    public UserDetailsImpl(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.status = user.getStatus();
        this.suspendedUntil = user.getSuspendedUntil();
    }

    // 권한 목록 반환 -> 현재는 모두 ROLE = USER
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // 비밀번호 반환
    @Override
    public String getPassword() {
        return password;
    }

    // Spring Security는 username을 식별자로 사용 -> email로 설정
    @Override
    public String getUsername() {
        return email;
    }

    // 계정 활성 상태 반환 ACTIVE -> true
    // 만료된 정지 - 로그인은 허용, 실제 복구는 loadUserByUsername이 처리
    @Override
    public boolean isEnabled() {
        return status != UserStatus.WITHDRAWN;
    }

}
