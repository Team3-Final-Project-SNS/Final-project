package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Inquiry 도메인의 관리자 도메인 호출용 내부 조회 기능을 제공하는 서비스
public interface InquiryInternalService {

    // 관리자용 단건 조회
    Inquiry getInquiryById(Long inquiryId);

    // 관리자용 목록 조회
    Page<Inquiry> getInquiriesForAdmin(
            InquiryAnswerStatus status,
            InquiryType type,
            Pageable pageable
    );
}