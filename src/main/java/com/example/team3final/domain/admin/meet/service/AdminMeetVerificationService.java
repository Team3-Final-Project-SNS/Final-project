package com.example.team3final.domain.admin.meet.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import org.springframework.data.domain.Pageable;

public interface AdminMeetVerificationService {

    // 노쇼 후보군 조회
    PageResponseDto<AdminNoShowCandidateResponseDto> getNoShowCandidates(Pageable pageable);
}
