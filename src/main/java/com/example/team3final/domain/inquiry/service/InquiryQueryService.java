package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import org.springframework.data.domain.Pageable;

// Inquiry 도메인의 사용자 문의 조회 기능을 담당하는 서비스
public interface InquiryQueryService {

    // 내 문의 상세조회(답변 포함)
    GetOneInquiryResponseDto getOneInquiry(Long userId, Long inquiryId);

    // 내 문의 목록 조회
    PageResponseDto<GetAllInquiriesResponseDto> getAllInquiries(Long userId, Pageable pageable);
}
