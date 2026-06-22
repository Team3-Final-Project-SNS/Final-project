package com.example.team3final.domain.admin.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;

    // 접수 알림은 특정 관리자 한 명이 아니라 모든 활성 관리자에게 전달한다.
    @Override
    public List<Long> getActiveAdminIds() {
        return adminRepository.findAllByIsActiveTrue().stream()
                .map(Admin::getId)
                .toList();
    }



    // AI 도매인에서 활용.
    @Override
    public void validateAdmin(Long adminId) {
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));
    }
}
