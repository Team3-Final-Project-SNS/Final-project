package com.example.team3final.domain.university.service;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;


import java.util.List;
import java.util.Map;

public interface UniversityService {

    // 활성화된 대학 목록을 조회합니다.
    List<UniversityResponseDto> getUniversities();

    // 회원가입 시 등록된 대학 도메인인지 검증하기 위한 조회
    boolean isRegisteredActiveUniversity(String emailDomain);

    // 이메일 도메인으로 학교 상세 정보 조회
    UniversityResponseDto getUniversityByDomain(String emailDomain);

    // Admin 도메인에서 사용할 universityId 목록
    Map<Long, String> getUniversityName(List<Long> universityIds);
}

