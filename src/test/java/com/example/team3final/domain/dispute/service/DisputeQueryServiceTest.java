package com.example.team3final.domain.dispute.service;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisputeQueryServiceTest {

    @InjectMocks
    private DisputeQueryServiceImpl disputeQueryService;

    @Mock
    private DisputeRepository disputeRepository;

    @Test
    @DisplayName("getMatchIdsWithActiveDispute returns empty set for null input")
    void getMatchIdsWithActiveDispute_NullInput_ReturnsEmptySet() {
        // when
        Set<Long> result = disputeQueryService.getMatchIdsWithActiveDispute(null);

        // then
        assertThat(result).isEmpty();
        verify(disputeRepository, never()).findMatchIdsByMatchIdInAndStatusIn(null, List.of());
    }

    @Test
    @DisplayName("getMatchIdsWithActiveDispute returns empty set for empty input")
    void getMatchIdsWithActiveDispute_EmptyInput_ReturnsEmptySet() {
        // when
        Set<Long> result = disputeQueryService.getMatchIdsWithActiveDispute(List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMatchIdsWithActiveDispute queries active statuses")
    void getMatchIdsWithActiveDispute_Success() {
        // given
        List<Long> matchIds = List.of(1L, 2L, 3L);
        List<DisputeStatus> activeStatuses = List.of(
                DisputeStatus.SUBMITTED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.HOLD
        );

        given(disputeRepository.findMatchIdsByMatchIdInAndStatusIn(matchIds, activeStatuses))
                .willReturn(List.of(1L, 1L, 3L));

        // when
        Set<Long> result = disputeQueryService.getMatchIdsWithActiveDispute(matchIds);

        // then
        assertThat(result).containsExactlyInAnyOrder(1L, 3L);
    }
}
