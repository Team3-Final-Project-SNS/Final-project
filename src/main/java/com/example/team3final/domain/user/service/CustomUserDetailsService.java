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

        // 기존 Spring Security 기본 User 대신 커스텀 UserDetailsImpl 반환
        // → userId를 포함하고 있어서 컨트롤러에서 바로 꺼낼 수 있음
        return new UserDetailsImpl(user);
    }
}
