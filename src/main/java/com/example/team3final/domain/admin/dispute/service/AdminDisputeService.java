package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;

public interface AdminDisputeService {

    GetAdminDisputeResponseDto getDispute(Long adminId, Long disputeId);
}
