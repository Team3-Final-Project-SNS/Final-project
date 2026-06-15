package com.example.team3final.domain.university.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UniversityException;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.entity.University;
import com.example.team3final.domain.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// University 도메인의 타 도메인 호출용 내부 조회/검증 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityInternalServiceImpl implements UniversityInternalService {

    private final UniversityRepository universityRepository;

    // 회원가입 시 등록된 대학 도메인인지 검증하기 위한 조회
    @Override
    public boolean isRegisteredActiveUniversity(String emailDomain) {
        return universityRepository.existsByeDomainAndIsActiveTrue(emailDomain);
    }

    // 도메인이 일치하고 활성화된 학교 단건 조회
    @Override
    public UniversityResponseDto getUniversityByDomain(String emailDomain) {
        University university = universityRepository.findByeDomainAndIsActiveTrue(emailDomain)
                .orElseThrow(() -> new UniversityException(ErrorCode.AUTH_UNREGISTERED_UNIVERSITY));
        return toUniversityResponseDto(university); // 기존 private 반환 메서드 재사용
    }

    // universityIds 조회
    @Override
    public Map<Long, String> getUniversityName(List<Long> universityIds) {
        return universityRepository.findAllById(universityIds)
                .stream()
                .collect(Collectors.toMap(
                        University::getId,
                        University::getUniversityName
                ));
    }

    private UniversityResponseDto toUniversityResponseDto(University university) {
        // University Entity를 API 응답 DTO로 변환합니다.
        return UniversityResponseDto.builder()
                .universityId(university.getId())
                .universityName(university.getUniversityName())
                .eDomain(university.getEDomain())
                .build();
    }
}
