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
class UserLocationCleanupServiceTest {

    @InjectMocks
    private UserLocationCleanupServiceImpl userLocationCleanupService;

    @Mock
    private UserLocationRepository userLocationRepository;

    @Test
    @DisplayName("deleteLocationsByMatchId deletes locations by match id")
    void deleteLocationsByMatchId_Success() {
        // when
        userLocationCleanupService.deleteLocationsByMatchId(1L);

        // then
        verify(userLocationRepository).deleteAllByMatchId(1L);
    }
}
