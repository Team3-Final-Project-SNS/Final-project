package com.example.team3final.domain.university.service;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.entity.University;
import com.example.team3final.domain.university.repository.UniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UniversityService {

    private final UniversityRepository universityRepository;

    public List<UniversityResponseDto> getUniversities() {
        return universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc()
                .stream()
                .map(this::toUniversityResponseDto) // Entity를 Response DTO로 변환합니다.
                .toList();
    }

    private UniversityResponseDto toUniversityResponseDto(University university) {
        return UniversityResponseDto.builder()
                .universityId(university.getId()) // Entity의 id를 API 응답용 universityId로 변환합니다.
                .universityName(university.getUniversityName()) // 학교명을 응답에 담습니다.
                .e_Domain(university.getE_Domain()) // 이메일 도메인을 응답에 담습니다.
                .build();
    }
}
