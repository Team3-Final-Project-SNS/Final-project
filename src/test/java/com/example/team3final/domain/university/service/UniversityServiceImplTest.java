package com.example.team3final.domain.university.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.UniversityException;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.entity.University;
import com.example.team3final.domain.university.repository.UniversityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UniversityServiceImplTest {

    @Mock
    private UniversityRepository universityRepository;

    @InjectMocks
    private UniversityServiceImpl universityService;

    @Test
    @DisplayName("대학 목록 조회 - 성공")
    void getUniversities_Success() {
        // given
        University university = University.builder()
                .universityName("Test University")
                .eDomain("test.ac.kr")
                .isActive(true)
                .build();
        given(universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc()).willReturn(List.of(university));

        // when
        List<UniversityResponseDto> result = universityService.getUniversities();

        // then
        assertEquals(1, result.size());
        assertEquals("Test University", result.get(0).universityName());
        assertEquals("test.ac.kr", result.get(0).eDomain());
    }

    @Test
    @DisplayName("대학 목록 조회 - 실패 (목록 비어있음)")
    void getUniversities_Fail_Empty() {
        // given
        given(universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc()).willReturn(Collections.emptyList());

        // when & then
        UniversityException exception = assertThrows(UniversityException.class, () -> universityService.getUniversities());
        assertEquals(ErrorCode.UNIVERSITY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("등록된 활성 대학 여부 확인 - 성공")
    void isRegisteredActiveUniversity_Success() {
        // given
        given(universityRepository.existsByeDomainAndIsActiveTrue(anyString())).willReturn(true);

        // when
        boolean result = universityService.isRegisteredActiveUniversity("test.ac.kr");

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("도메인으로 대학 조회 - 성공")
    void getUniversityByDomain_Success() {
        // given
        University university = mock(University.class);
        given(university.getId()).willReturn(1L);
        given(university.getUniversityName()).willReturn("Test University");
        given(university.getEDomain()).willReturn("test.ac.kr");
        given(universityRepository.findByeDomainAndIsActiveTrue("test.ac.kr")).willReturn(Optional.of(university));

        // when
        UniversityResponseDto result = universityService.getUniversityByDomain("test.ac.kr");

        // then
        assertNotNull(result);
        assertEquals("Test University", result.universityName());
    }

    @Test
    @DisplayName("도메인으로 대학 조회 - 실패 (등록되지 않은 학교)")
    void getUniversityByDomain_Fail() {
        // given
        given(universityRepository.findByeDomainAndIsActiveTrue(anyString())).willReturn(Optional.empty());

        // when & then
        UniversityException exception = assertThrows(UniversityException.class, () -> universityService.getUniversityByDomain("unknown.ac.kr"));
        assertEquals(ErrorCode.AUTH_UNREGISTERED_UNIVERSITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("대학 ID 목록으로 대학명 맵 조회 - 성공")
    void getUniversityName_Success() {
        // given
        University u1 = mock(University.class);
        given(u1.getId()).willReturn(1L);
        given(u1.getUniversityName()).willReturn("Univ 1");

        University u2 = mock(University.class);
        given(u2.getId()).willReturn(2L);
        given(u2.getUniversityName()).willReturn("Univ 2");

        given(universityRepository.findAllById(anyList())).willReturn(List.of(u1, u2));

        // when
        Map<Long, String> result = universityService.getUniversityName(List.of(1L, 2L));

        // then
        assertEquals(2, result.size());
        assertEquals("Univ 1", result.get(1L));
        assertEquals("Univ 2", result.get(2L));
    }
}
