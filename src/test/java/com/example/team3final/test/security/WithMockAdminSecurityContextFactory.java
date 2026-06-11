package com.example.team3final.test.security;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.test.util.ReflectionTestUtils;

public class WithMockAdminSecurityContextFactory implements WithSecurityContextFactory<WithMockAdmin> {

    @Override
    public SecurityContext createSecurityContext(WithMockAdmin annotation) {
        Admin admin = Admin.builder()
                .email(annotation.email())
                .password("password")
                .name("admin")
                .role(annotation.role())
                .build();
        ReflectionTestUtils.setField(admin, "id", annotation.adminId());

        AdminDetailsImpl principal = new AdminDetailsImpl(admin);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
