package com.example.team3final.domain.university.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UniversityInternalService 단위 테스트")
class UniversityInternalServiceTest {

    @Mock
    private UniversityRepository universityRepository;

    @InjectMocks
    private UniversityInternalServiceImpl universityInternalService;

    @Test
    @DisplayName("이메일 도메인이 활성 대학교 도메인인지 확인한다")
    void isRegisteredActiveUniversity_shouldReturnExistsResult() {
        when(universityRepository.existsByeDomainAndIsActiveTrue("test.ac.kr")).thenReturn(true);

        boolean response = universityInternalService.isRegisteredActiveUniversity("test.ac.kr");

        assertThat(response).isTrue();
    }

    @Test
    @DisplayName("활성 대학교 도메인으로 대학교 정보를 조회한다")
    void getUniversityByDomain_shouldReturnUniversity() {
        University university = university();
        when(universityRepository.findByeDomainAndIsActiveTrue("test.ac.kr")).thenReturn(Optional.of(university));

        UniversityResponseDto response = universityInternalService.getUniversityByDomain("test.ac.kr");

        assertThat(response.universityId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("활성 대학교 도메인이 없으면 조회에 실패한다")
    void getUniversityByDomain_shouldThrowWhenNotFound() {
        when(universityRepository.findByeDomainAndIsActiveTrue("test.ac.kr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> universityInternalService.getUniversityByDomain("test.ac.kr"))
                .isInstanceOf(UniversityException.class);
    }

    @Test
    @DisplayName("대학교 ID 목록으로 대학교 이름 맵을 조회한다")
    void getUniversityName_shouldReturnNameMap() {
        University university = university();
        when(universityRepository.findAllById(List.of(1L))).thenReturn(List.of(university));

        Map<Long, String> response = universityInternalService.getUniversityName(List.of(1L));

        assertThat(response).containsEntry(1L, "테스트대학교");
    }

    private University university() {
        University university = University.builder()
                .universityName("테스트대학교")
                .eDomain("test.ac.kr")
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(university, "id", 1L);
        return university;
    }
}
