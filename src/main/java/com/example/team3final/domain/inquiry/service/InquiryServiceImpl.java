package com.example.team3final.domain.inquiry.service;

import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService{

    private final InquiryRepository inquiryRepository;

    // 고객 문의 접수
    @Override
    @Transactional
    public CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request) {
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .inquiryType(request.getType())
                .build();

        // DB에 저장
        Inquiry savedInquiry = inquiryRepository.save(inquiry);

        return CreateInquiryResponseDto.from(savedInquiry);
    }
}
