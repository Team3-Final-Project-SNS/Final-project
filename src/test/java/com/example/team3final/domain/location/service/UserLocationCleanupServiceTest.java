package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.repository.UserLocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserLocationCleanupService 단위 테스트")
class UserLocationCleanupServiceTest {

    @Mock
    private UserLocationRepository userLocationRepository;

    @InjectMocks
    private UserLocationCleanupServiceImpl userLocationCleanupService;

    @Test
    @DisplayName("매치 ID에 해당하는 위치 정보를 삭제한다")
    void deleteLocationsByMatchId_shouldDeleteLocations() {
        userLocationCleanupService.deleteLocationsByMatchId(10L);

        verify(userLocationRepository).deleteAllByMatchId(10L);
    }
}
