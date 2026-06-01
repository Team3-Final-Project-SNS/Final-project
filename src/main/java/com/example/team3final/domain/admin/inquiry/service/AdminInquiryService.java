package com.example.team3final.domain.admin.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import org.springframework.data.domain.Pageable;

public interface AdminInquiryService {

    // 관리자 문의 상세 조회
    AdminGetInquiryResponseDto getInquiry(Long adminId, Long inquiryId);

    // 관리자 문의 목록 조회
    PageResponseDto<AdminGetInquiriesResponseDto> getInquiries(
            Long adminId, InquiryAnswerStatus status, InquiryType type, Pageable pageable);

    // 고객 문의 답변
    AdminCreateInquiryResponseDto createAnswer(Long adminId, Long inquiryId, AdminCreateInquiryRequestDto requestDto);
}
