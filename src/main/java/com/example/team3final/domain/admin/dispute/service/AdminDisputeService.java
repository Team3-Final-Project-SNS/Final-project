package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;

public interface AdminDisputeService {

    // 이의 제기 상세조회
    GetAdminDisputeResponseDto getDispute(Long adminId, Long disputeId);
}
