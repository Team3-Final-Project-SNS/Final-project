package com.example.team3final.domain.user.service;

import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // 로그인 시 자동 호출
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("유저를 찾을 수 없습니다: " + email));

        // Spring Security의 UserDetails 구현체로 변환
        // User.builder()가 아닌 Spring Security의 User.builder()
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())          // 식별자 = 이메일
                .password(user.getPassword())       // BCrypt 암호화된 비밀번호
                .disabled(!user.isActive())         // 정지/탈퇴 계정 비활성화
                .roles("USER")                      // 권한 (현재는 모두 USER)
                .build();
    }
}
