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

// University 도메인의 조회 API 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityQueryServiceImpl implements UniversityQueryService {

    private final UniversityRepository universityRepository;

    @Override
    public List<UniversityResponseDto> getUniversities() {
        // 활성화된 대학 목록을 조회합니다.
        List<University> universities = universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc();

        // 활성화된 대학이 하나도 없으면 공통 예외 응답으로 처리합니다.
        if (universities.isEmpty()) {
            throw new UniversityException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }

        // Entity 목록을 Response DTO 목록으로 변환합니다.
        return universities.stream()
                .map(this::toUniversityResponseDto)
                .toList();
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
