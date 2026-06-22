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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UniversityQueryService 단위 테스트")
class UniversityQueryServiceTest {

    @Mock
    private UniversityRepository universityRepository;

    @InjectMocks
    private UniversityQueryServiceImpl universityQueryService;

    @Test
    @DisplayName("활성 대학교 목록을 이름순 조회 결과로 반환한다")
    void getUniversities_shouldReturnActiveUniversities() {
        University university = University.builder()
                .universityName("테스트대학교")
                .eDomain("test.ac.kr")
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(university, "id", 1L);
        when(universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc())
                .thenReturn(List.of(university));

        List<UniversityResponseDto> result = universityQueryService.getUniversities();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).universityId()).isEqualTo(1L);
        assertThat(result.get(0).universityName()).isEqualTo("테스트대학교");
        assertThat(result.get(0).eDomain()).isEqualTo("test.ac.kr");
        verify(universityRepository).findAllByIsActiveTrueOrderByUniversityNameAsc();
    }

    @Test
    @DisplayName("활성 대학교가 없으면 대학교 예외를 던진다")
    void getUniversities_shouldThrowWhenNoActiveUniversity() {
        when(universityRepository.findAllByIsActiveTrueOrderByUniversityNameAsc())
                .thenReturn(List.of());

        assertThatThrownBy(() -> universityQueryService.getUniversities())
                .isInstanceOf(UniversityException.class);

        verify(universityRepository).findAllByIsActiveTrueOrderByUniversityNameAsc();
    }
}
