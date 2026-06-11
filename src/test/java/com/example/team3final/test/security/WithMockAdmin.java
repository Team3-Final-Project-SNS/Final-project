package com.example.team3final.test.security;

import com.example.team3final.domain.admin.enums.AdminRole;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockAdminSecurityContextFactory.class)
public @interface WithMockAdmin {

    long adminId() default 1L;

    String email() default "admin@test.com";

    AdminRole role() default AdminRole.SUPER_ADMIN;
}
