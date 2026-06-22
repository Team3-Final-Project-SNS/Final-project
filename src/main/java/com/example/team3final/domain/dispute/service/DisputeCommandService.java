package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.MyDisputeResponseDto;

import java.util.List;

// Dispute 도메인의 사용자 요청 기반 기능을 담당하는 서비스
public interface DisputeCommandService {

    // 이의제기 제출
    CreateDisputeResponseDto createDispute(
            Long matchId,
            Long userId,
            CreateDisputeRequestDto request
    );

    // 재이의제기 제출
    CreateDisputeResponseDto reCreateDispute(
            Long matchId,
            Long userId,
            CreateDisputeRequestDto request
    );

    // 특정 매칭에 대해 내가 제출한 이의제기 상세 조회
    DisputeResponseDto getDispute(Long matchId, Long userId);

    // 내가 제출한 이의제기 전체 목록 조회
    // matchId 없이 userId 만으로 내 모든 이의제기를 최신순으로 반환
    List<MyDisputeResponseDto> getMyDisputes(Long userId);
}