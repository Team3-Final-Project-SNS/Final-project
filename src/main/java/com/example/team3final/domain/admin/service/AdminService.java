package com.example.team3final.domain.admin.service;

import java.util.List;

// 관리자 공통 서비스 인터페이스
// 여러 도메인에서 관리자 정보가 필요할 때 사용
public interface AdminService {

    // 활성 관리자 ID 전체 조회
    // 신고/문의/이의제기 접수 시 관리자 알림 발송용
    List<Long> getActiveAdminIds();


    // AI 도매인의 사용.
    // 관리자 ID가 실제 관리자 계정인지 검증
    void validateAdmin(Long adminId);
}
