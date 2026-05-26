package com.example.team3final.domain.admin.security;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDetailsService {

    private final AdminRepository adminRepository;

    // 메서드명도 변경 (UserDetailsService 계약에서 벗어남)
    public AdminDetailsImpl loadAdminByEmail(String email) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("관리자를 찾을 수 없습니다: " + email));
        return new AdminDetailsImpl(admin);
    }
}
