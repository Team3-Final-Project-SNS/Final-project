package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AdminInquiryAnswerService {

    // 유저용 — 문의 ID로 답변 조회 (InquiryServiceImpl에서 호출)
    Optional<InquiryAnswer> getByInquiryId(Long inquiryId);

    // 관리자 문의 상세 조회
    AdminGetInquiryResponseDto getInquiry(Long adminId, Long inquiryId);

    // 관리자 문의 목록 조회
    PageResponseDto<AdminGetInquiriesResponseDto> getInquiries(
            Long adminId, InquiryAnswerStatus status, InquiryType type, Pageable pageable);

    // 고객 문의 답변 생성
    AdminCreateInquiryResponseDto createAnswer(
            Long adminId, Long inquiryId, AdminCreateInquiryRequestDto requestDto);
}