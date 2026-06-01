package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Pageable;

public interface AdminDisputeService {

    // 이의 제기 상세조회
    GetAdminDisputeResponseDto getDispute(Long adminId, Long disputeId);

    // 이의 제기 목록조회
    PageResponseDto<GetAdminDisputesResponseDto> getDisputes(Long adminId, DisputeStatus status, Pageable pageable);

    // 이의제기 최종 판정
    AdminJudgeDisputeResponseDto judgeDispute(Long adminId, Long disputeId, AdminJudgeDisputeRequestDto requestDto);
}
