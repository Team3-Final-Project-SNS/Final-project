package com.example.team3final.domain.admin.inquiryAnswer.repository;

import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, Long> {

    // inquiryId로 답변 1건 조회
    Optional<InquiryAnswer> findByInquiryId(Long inquiryId);
}
