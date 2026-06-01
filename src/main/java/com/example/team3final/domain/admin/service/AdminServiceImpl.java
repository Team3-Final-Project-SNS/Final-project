package com.example.team3final.domain.admin.service;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;

    // 활성 관리자 ID 단건 조회
    // isActive = true인 관리자 1명만 조회
    // 관리자가 없거나 비활성화된 경우 null 반환 → 호출 측에서 null 체크 후 알림 스킵
    @Override
    public Long getAdminId() {
        return adminRepository.findFirstByIsActiveTrue()
                .map(Admin::getId)
                .orElse(null);
    }
}