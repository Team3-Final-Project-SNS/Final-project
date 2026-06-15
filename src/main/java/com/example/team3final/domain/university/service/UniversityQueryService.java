package com.example.team3final.domain.university.service;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;

import java.util.List;

// University 도메인의 조회 API 기능을 담당하는 서비스
public interface UniversityQueryService {

    // 활성화된 대학 목록을 조회합니다.
    List<UniversityResponseDto> getUniversities();
}
