package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;

// Inquiry 도메인의 문의 생성/취소 등 사용자 요청 기반 변경 작업을 담당하는 서비스
public interface InquiryCommandService {

    // 고객 문의 접수
    CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request);

    // 고객 문의 취소
    CancelInquiryResponseDto cancelInquiry(Long userId, Long inquiryId);
}
