package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Inquiry 도메인의 사용자 문의 조회 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryQueryServiceImpl implements InquiryQueryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;

    // 내 문의 상세(답변) 조회
    @Override
    public GetOneInquiryResponseDto getOneInquiry(Long userId, Long inquiryId) {

        // 문의 존재 여부 확인
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new InquiryException(ErrorCode.INQUIRY_NOT_FOUND));

        // 본인 문의인지 확인
        if (!inquiry.getUserId().equals(userId)) {
            throw new InquiryException(ErrorCode.INQUIRY_ACCESS_DENIED);
        }

        // 답변 조회 — 순환참조 방지를 위해 Repository 직접 사용
        InquiryAnswer answer = inquiryAnswerRepository.findByInquiryId(inquiryId)
                .orElse(null);

        return GetOneInquiryResponseDto.of(inquiry, answer);
    }

    // 내 문의 목록 조회
    @Override
    public PageResponseDto<GetAllInquiriesResponseDto> getAllInquiries(Long userId, Pageable pageable) {

        // userId로 본인 문의만 최신순 페이징 조회
        Page<Inquiry> inquiryPage = inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        // Page<Inquiry> → Page<DTO> 변환
        Page<GetAllInquiriesResponseDto> dtoPage = inquiryPage.map(GetAllInquiriesResponseDto::from);

        return PageResponseDto.from(dtoPage);
    }
}
