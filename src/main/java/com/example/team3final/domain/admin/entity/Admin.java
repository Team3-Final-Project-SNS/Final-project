package com.example.team3final.domain.admin.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.common.entity.BaseUpdateEntity;
import com.example.team3final.domain.admin.enums.AdminRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseUpdateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AdminRole role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Builder
    private Admin(String email, String password, String name, AdminRole role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.isActive = true; // 계정 생성시 바로 활성화 상태
    }

    public static Admin createAdmin(String email, String encodedPassword, String name, AdminRole role) {
        return Admin.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .role(AdminRole.SUPER_ADMIN) // 현재는 SUPER_ADMIN만 있기 때문에 이 역할로 고정
                .build();
    }

    // 계정 비활성화
    public void deactivate() {
        this.isActive = false;
    }

    // 계정 재활성화
    public void reActivate() {
        this.isActive = true;
    }

    // 활성화된 Admin 계정인지 확인
    public boolean isActiveAdmin() {
        return this.isActive();
    }

    // SUPER_ADMIN이 맞는지 확인
    public boolean isSuperAdmin() {
        return this.role == AdminRole.SUPER_ADMIN;
    }

    // 위 두 로직을 한꺼번에 처리
    public boolean isActiveAndSuperAdmin() {
        return isActiveAdmin() && isSuperAdmin();
    }
}
