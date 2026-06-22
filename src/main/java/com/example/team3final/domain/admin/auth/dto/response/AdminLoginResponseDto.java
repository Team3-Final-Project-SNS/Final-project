package com.example.team3final.domain.admin.auth.dto.response;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;

public record AdminLoginResponseDto(

        Long adminId,
        String name,
        AdminRole role,
        String adminAccessToken
) {
    public static AdminLoginResponseDto of(Admin admin, String adminAccessToken) {
        return new AdminLoginResponseDto(
                admin.getId(),
                admin.getName(),
                admin.getRole(),
                adminAccessToken
        );
    }
}
