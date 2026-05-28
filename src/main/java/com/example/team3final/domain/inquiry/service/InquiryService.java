package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetMyInquiriesResponseDto;

public interface InquiryService {

    // 고객 문의 접수
    CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request);

    // 문의 상세조회(답변 포함)
    GetInquiryResponseDto getInquiry(Long userId, Long inquiryId);
}
