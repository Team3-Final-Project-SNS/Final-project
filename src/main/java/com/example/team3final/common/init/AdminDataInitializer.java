package com.example.team3final.common.init;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.init.email}")
    private String adminEmail;

    @Value("${admin.init.password}")
    private String adminPassword;

    @Value("${admin.init.name}")
    private String adminName;

    @Override
    public void run(ApplicationArguments args) {

        if (adminRepository.count() == 0) {
            Admin admin = Admin.createAdmin(
                    adminEmail,
                    passwordEncoder.encode(adminPassword),
                    adminName,
                    AdminRole.SUPER_ADMIN
            );

            adminRepository.save(admin);
            log.info("[AdminDataInitializer] 초기 관리자 계정 생성 완료 - email: {}", admin.getEmail());
        }
    }
}
