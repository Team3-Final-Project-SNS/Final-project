package com.example.team3final.domain.admin.security;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AdminDetailsImpl implements UserDetails {

    private final Long adminId;
    private final String email;
    private final String password;
    private final AdminRole role;
    private final boolean active;

    public AdminDetailsImpl(Admin admin) {
        this.adminId = admin.getId();
        this.email = admin.getEmail();
        this.password = admin.getPassword();
        this.role = admin.getRole();
        this.active = admin.isActive();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
