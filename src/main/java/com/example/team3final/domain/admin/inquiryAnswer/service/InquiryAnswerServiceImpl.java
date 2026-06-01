package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryAnswerServiceImpl implements InquiryAnswerService {

    private final InquiryAnswerRepository inquiryAnswerRepository;

    @Override
    public Optional<InquiryAnswer> getByInquiryId(Long inquiryId) {

        return inquiryAnswerRepository.findByInquiryId(inquiryId);
    }

    // 답변 생성 후 저장
    @Override
    @Transactional
    public InquiryAnswer createAnswer(Long adminId, Long inquiryId, String adminName, String content) {

        // InquiryAnswer 생성
        InquiryAnswer inquiryAnswer = InquiryAnswer.builder()
                .inquiryId(inquiryId)
                .adminId(adminId)
                .adminName(adminName)
                .content(content)
                .build();

        return inquiryAnswerRepository.save(inquiryAnswer);
    }
}
