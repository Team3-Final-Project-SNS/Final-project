package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryService {

    // 고객 문의 접수
    CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request);

    // 내 문의 상세조회(답변 포함)
    GetOneInquiryResponseDto getOneInquiry(Long userId, Long inquiryId);

    // 내 문의 목록 조회
    PageResponseDto<GetAllInquiriesResponseDto> getAllInquiries(Long userId, Pageable pageable);

    // 고객 문의 취소
    CancelInquiryResponseDto cancelInquiry(Long userId, Long inquiryId);

    // 관리자용 단건 조회
    Inquiry getInquiryById(Long inquiryId);

    // 관리자용 목록 조회
    Page<Inquiry> getInquiriesForAdmin(InquiryAnswerStatus status, InquiryType type, Pageable pageable);
}
