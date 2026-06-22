package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchInternalService 단위 테스트")
class MatchInternalServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchInternalServiceImpl matchInternalService;

    @Test
    @DisplayName("매칭 ID로 내부 조회하면 매칭 정보를 반환한다")
    void getMatchInfo_shouldReturnMatchInfo() {
        Match match = match();
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        MatchInfoDto response = matchInternalService.getMatchInfo(10L);

        assertThat(response.matchId()).isEqualTo(10L);
        assertThat(response.postId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("매칭이 없으면 내부 조회에 실패한다")
    void getMatchById_shouldThrowWhenMatchNotFound() {
        when(matchRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchInternalService.getMatchById(10L))
                .isInstanceOf(MatchException.class);
    }

    @Test
    @DisplayName("매칭 ID 목록이 비어 있으면 저장소 조회 없이 빈 맵을 반환한다")
    void getMatchInfos_shouldReturnEmptyMapWhenIdsEmpty() {
        Map<Long, MatchInfoDto> response = matchInternalService.getMatchInfos(List.of());

        assertThat(response).isEmpty();
        verifyNoInteractions(matchRepository);
    }

    @Test
    @DisplayName("완료된 매칭만 리뷰 작성 가능 매칭으로 반환한다")
    void findCompletedMatchById_shouldReturnOnlyCompletedMatch() {
        Match match = match();
        match.complete();
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        Optional<Match> response = matchInternalService.findCompletedMatchById(10L);

        assertThat(response).contains(match);
    }

    private Match match() {
        Match match = Match.builder()
                .postId(20L)
                .applicantId(2L)
                .applicantDeposit(200)
                .build();
        ReflectionTestUtils.setField(match, "id", 10L);
        ReflectionTestUtils.setField(match, "status", MatchStatus.MATCHED);
        return match;
    }
}
