package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Inquiry 도메인의 관리자 도메인 호출용 내부 조회 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryInternalServiceImpl implements InquiryInternalService {

    private final InquiryRepository inquiryRepository;

    // 관리자용 단건 조회
    @Override
    public Inquiry getInquiryById(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    // 관리자용 목록 조회
    @Override
    public Page<Inquiry> getInquiriesForAdmin(InquiryAnswerStatus status, InquiryType type, Pageable pageable) {
        return inquiryRepository.findAllByStatusAndType(status, type, pageable);
    }
}
