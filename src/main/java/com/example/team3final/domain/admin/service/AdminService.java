package com.example.team3final.domain.admin.service;

// 관리자 공통 서비스 인터페이스
// 여러 도메인에서 관리자 정보가 필요할 때 사용
public interface AdminService {

    // 활성 관리자 ID 단건 조회
    // 신고/문의/이의제기 접수 시 관리자 알림 발송용
    // 관리자가 없거나 비활성화된 경우 null 반환
    Long getAdminId();
}