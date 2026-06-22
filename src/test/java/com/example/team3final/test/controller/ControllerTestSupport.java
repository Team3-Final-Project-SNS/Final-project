package com.example.team3final.test.controller;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

public abstract class ControllerTestSupport {

    protected MockMvc mockMvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();
    }

    protected Authentication userAuthentication(Long userId) {
        User user = User.builder()
                .email("user" + userId + "@test.ac.kr")
                .password("password")
                .name("test user")
                .nickname("tester" + userId)
                .universityId(1L)
                .major("Computer Science")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        UserDetailsImpl principal = new UserDetailsImpl(user);
        return setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        ));
    }

    protected Authentication adminAuthentication(Long adminId) {
        Admin admin = Admin.builder()
                .email("admin" + adminId + "@test.com")
                .password("password")
                .name("admin")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", adminId);
        AdminDetailsImpl principal = new AdminDetailsImpl(admin);
        return setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        ));
    }

    private Authentication setAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        return authentication;
    }
}
