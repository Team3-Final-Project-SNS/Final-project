package com.example.team3final.domain.university.service;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.entity.University;
import com.example.team3final.domain.university.repository.UniversityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

    @InjectMocks
    private UniversityServiceImpl universityService;

    @Mock
    private UniversityRepository universityRepository;

    @Test
    @DisplayName("대학 목록 조회 - 성공")
    void getUniversities_Success() {
        // given
        University univ = mock(University.class);
        given(univ.getId()).willReturn(1L);
        given(univ.getUniversityName()).willReturn("Test Univ");
        given(univ.getEDomain()).willReturn("test.ac.kr");

        given(universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc()).willReturn(List.of(univ));

        // when
        List<UniversityResponseDto> result = universityService.getUniversities();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).universityName()).isEqualTo("Test Univ");
    }

    @Test
    @DisplayName("등록된 활성 대학 여부 확인 - 성공")
    void isRegisteredActiveUniversity_Success() {
        given(universityRepository.existsByeDomainAndIsActiveTrue("test.ac.kr")).willReturn(true);

        boolean result = universityService.isRegisteredActiveUniversity("test.ac.kr");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("도메인으로 대학 조회 - 성공")
    void getUniversityByDomain_Success() {
        University univ = mock(University.class);
        given(univ.getId()).willReturn(1L);
        given(univ.getUniversityName()).willReturn("Test Univ");
        given(univ.getEDomain()).willReturn("test.ac.kr");
        given(universityRepository.findByeDomainAndIsActiveTrue("test.ac.kr")).willReturn(Optional.of(univ));

        UniversityResponseDto result = universityService.getUniversityByDomain("test.ac.kr");

        assertThat(result.universityId()).isEqualTo(1L);
        assertThat(result.eDomain()).isEqualTo("test.ac.kr");
    }

    @Test
    @DisplayName("대학 이름 맵 조회 - 성공")
    void getUniversityName_Success() {
        University univ = mock(University.class);
        given(univ.getId()).willReturn(1L);
        given(univ.getUniversityName()).willReturn("Test Univ");
        given(universityRepository.findAllById(List.of(1L))).willReturn(List.of(univ));

        Map<Long, String> result = universityService.getUniversityName(List.of(1L));

        assertThat(result).containsEntry(1L, "Test Univ");
    }
}
