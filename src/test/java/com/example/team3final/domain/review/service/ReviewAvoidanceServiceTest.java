package com.example.team3final.domain.review.service;

import com.example.team3final.domain.review.entity.UserAvoidRelation;
import com.example.team3final.domain.review.repository.UserAvoidRelationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewAvoidanceServiceTest {

    @InjectMocks
    private ReviewAvoidanceServiceImpl reviewAvoidanceService;

    @Mock
    private UserAvoidRelationRepository userAvoidRelationRepository;

    @Test
    @DisplayName("getAvoidedUserIds returns avoided user ids")
    void getAvoidedUserIds_Success() {
        // given
        given(userAvoidRelationRepository.findAllByUserId(1L)).willReturn(List.of(
                UserAvoidRelation.builder().userId(1L).avoidedUserId(2L).reviewId(10L).build(),
                UserAvoidRelation.builder().userId(1L).avoidedUserId(3L).reviewId(11L).build()
        ));

        // when
        List<Long> result = reviewAvoidanceService.getAvoidedUserIds(1L);

        // then
        assertThat(result).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("existsAvoidRelation delegates to repository")
    void existsAvoidRelation_Success() {
        // given
        given(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(1L, 2L))
                .willReturn(true);

        // when
        boolean result = reviewAvoidanceService.existsAvoidRelation(1L, 2L);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("createAvoidRelation creates bidirectional relations")
    void createAvoidRelation_Success() {
        // given
        given(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(1L, 2L))
                .willReturn(false);
        given(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(2L, 1L))
                .willReturn(false);

        // when
        reviewAvoidanceService.createAvoidRelation(1L, 2L, 10L);

        // then
        ArgumentCaptor<UserAvoidRelation> captor = ArgumentCaptor.forClass(UserAvoidRelation.class);
        verify(userAvoidRelationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserAvoidRelation::getUserId, UserAvoidRelation::getAvoidedUserId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L),
                        org.assertj.core.groups.Tuple.tuple(2L, 1L)
                );
    }

    @Test
    @DisplayName("createAvoidRelation skips existing directions")
    void createAvoidRelation_ExistingRelation_SkipsSave() {
        // given
        given(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(1L, 2L))
                .willReturn(true);
        given(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(2L, 1L))
                .willReturn(true);

        // when
        reviewAvoidanceService.createAvoidRelation(1L, 2L, 10L);

        // then
        verify(userAvoidRelationRepository, times(0)).save(org.mockito.ArgumentMatchers.any());
    }
}
