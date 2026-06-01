package com.example.team3final.domain.admin.inquiryAnswer.service;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;

import java.util.Optional;

public interface InquiryAnswerService {

    // inquiryId로 답변 1건 조회
    Optional<InquiryAnswer> getByInquiryId(Long inquiryId);

    // 답변 생성
    InquiryAnswer createAnswer(Long adminId, Long inquiryId, String adminName, String content);
}
