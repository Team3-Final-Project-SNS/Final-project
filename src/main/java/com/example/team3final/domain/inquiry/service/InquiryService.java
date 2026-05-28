package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;

public interface InquiryService {

    // 고객 문의 접수
    CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request);
}
