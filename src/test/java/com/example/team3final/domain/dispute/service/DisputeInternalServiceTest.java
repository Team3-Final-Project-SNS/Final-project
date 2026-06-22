package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeInternalService 단위 테스트")
class DisputeInternalServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @InjectMocks
    private DisputeInternalServiceImpl disputeInternalService;

    @Test
    @DisplayName("이의제기 ID로 이의제기 엔티티를 조회한다")
    void getDisputeById_shouldReturnDispute() {
        Dispute dispute = dispute(1L, 10L, 1L, DisputeStatus.SUBMITTED);
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(dispute));

        Dispute result = disputeInternalService.getDisputeById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(disputeRepository).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이의제기 ID 조회는 이의제기 예외를 던진다")
    void getDisputeById_shouldThrowWhenNotFound() {
        when(disputeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeInternalService.getDisputeById(1L))
                .isInstanceOf(DisputeException.class);
    }

    @Test
    @DisplayName("관리자 이의제기 목록 조회는 상태가 없으면 전체 목록을 조회한다")
    void getDisputesForAdmin_shouldFindAllWhenStatusIsNull() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Dispute> page = new PageImpl<>(List.of(dispute(1L, 10L, 1L, DisputeStatus.SUBMITTED)), pageable, 1);
        when(disputeRepository.findAll(pageable)).thenReturn(page);

        Page<Dispute> result = disputeInternalService.getDisputesForAdmin(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(disputeRepository).findAll(pageable);
    }

    @Test
    @DisplayName("관리자 이의제기 목록 조회는 상태가 있으면 해당 상태로 조회한다")
    void getDisputesForAdmin_shouldFindByStatusWhenStatusExists() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Dispute> page = new PageImpl<>(List.of(dispute(1L, 10L, 1L, DisputeStatus.HOLD)), pageable, 1);
        when(disputeRepository.findAllByStatus(DisputeStatus.HOLD, pageable)).thenReturn(page);

        Page<Dispute> result = disputeInternalService.getDisputesForAdmin(DisputeStatus.HOLD, pageable);

        assertThat(result.getContent().get(0).getStatus()).isEqualTo(DisputeStatus.HOLD);
        verify(disputeRepository).findAllByStatus(DisputeStatus.HOLD, pageable);
    }

    @Test
    @DisplayName("매치 ID 목록이 비어 있으면 이의제기 매치 ID 조회는 저장소를 호출하지 않는다")
    void getMatchIdsWithDispute_shouldReturnEmptySetWhenMatchIdsEmpty() {
        Set<Long> result = disputeInternalService.getMatchIdsWithDispute(List.of());

        assertThat(result).isEmpty();
        verify(disputeRepository, never()).findMatchIdsByMatchIdIn(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("진행 중인 이의제기가 있는 매치 ID를 중복 없이 반환한다")
    void getMatchIdsWithActiveDispute_shouldReturnActiveDisputeMatchIds() {
        when(disputeRepository.findMatchIdsByMatchIdInAndStatusIn(
                List.of(10L, 11L),
                List.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW, DisputeStatus.HOLD)))
                .thenReturn(List.of(10L, 10L));

        Set<Long> result = disputeInternalService.getMatchIdsWithActiveDispute(List.of(10L, 11L));

        assertThat(result).containsExactly(10L);
    }

    private Dispute dispute(Long disputeId, Long matchId, Long submitterId, DisputeStatus status) {
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(submitterId)
                .disputeType(DisputeType.GPS_ERROR)
                .reason("GPS 오류")
                .build();
        ReflectionTestUtils.setField(dispute, "id", disputeId);
        ReflectionTestUtils.setField(dispute, "status", status);
        return dispute;
    }
}
