package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;

public interface DisputeService {

    /**
     * 이의제기 제출.
     * @param matchId 대상 매칭 ID (URL path)
     * @param userId  요청자 ID (인증 토큰에서 추출)
     * @param request 사유
     */
    CreateDisputeResponseDto createDispute(Long matchId, Long userId, CreateDisputeRequestDto request);

//    /**
//     * 내가 제출한 이의제기 상태 조회.
//     */
//    DisputeResponseDto getDispute(Long matchId, Long userId);
}
