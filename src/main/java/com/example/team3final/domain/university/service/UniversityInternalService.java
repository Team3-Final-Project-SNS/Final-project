package com.example.team3final.domain.university.service;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;

import java.util.List;
import java.util.Map;

// University 도메인의 타 도메인 호출용 내부 조회/검증 기능을 제공하는 서비스
public interface UniversityInternalService {

    // 회원가입 시 등록된 대학 도메인인지 검증하기 위한 조회
    boolean isRegisteredActiveUniversity(String emailDomain);

    // 이메일 도메인으로 학교 상세 정보 조회
    UniversityResponseDto getUniversityByDomain(String emailDomain);

    // Admin 도메인에서 사용할 universityId 목록
    Map<Long, String> getUniversityName(List<Long> universityIds);
}
