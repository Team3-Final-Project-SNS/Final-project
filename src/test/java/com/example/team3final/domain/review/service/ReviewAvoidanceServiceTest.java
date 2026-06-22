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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewAvoidanceService 단위 테스트")
class ReviewAvoidanceServiceTest {

    @Mock
    private UserAvoidRelationRepository userAvoidRelationRepository;

    @InjectMocks
    private ReviewAvoidanceServiceImpl reviewAvoidanceService;

    @Test
    @DisplayName("사용자가 회피한 사용자 ID 목록을 조회한다")
    void getAvoidedUserIds_shouldReturnAvoidedUserIds() {
        when(userAvoidRelationRepository.findAllByUserId(1L))
                .thenReturn(List.of(
                        UserAvoidRelation.builder().userId(1L).avoidedUserId(2L).reviewId(10L).build(),
                        UserAvoidRelation.builder().userId(1L).avoidedUserId(3L).reviewId(11L).build()));

        List<Long> result = reviewAvoidanceService.getAvoidedUserIds(1L);

        assertThat(result).containsExactly(2L, 3L);
        verify(userAvoidRelationRepository).findAllByUserId(1L);
    }

    @Test
    @DisplayName("두 사용자 사이의 회피 관계 존재 여부를 조회한다")
    void existsAvoidRelation_shouldReturnExistence() {
        when(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(1L, 2L)).thenReturn(true);

        boolean result = reviewAvoidanceService.existsAvoidRelation(1L, 2L);

        assertThat(result).isTrue();
        verify(userAvoidRelationRepository).existsByUserIdAndAvoidedUserId(1L, 2L);
    }

    @Test
    @DisplayName("회피 관계 생성은 양방향 관계를 저장한다")
    void createAvoidRelation_shouldSaveBidirectionalRelations() {
        when(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(1L, 2L)).thenReturn(false);
        when(userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(2L, 1L)).thenReturn(false);
        ArgumentCaptor<UserAvoidRelation> captor = ArgumentCaptor.forClass(UserAvoidRelation.class);

        reviewAvoidanceService.createAvoidRelation(1L, 2L, 10L);

        verify(userAvoidRelationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserAvoidRelation::getUserId, UserAvoidRelation::getAvoidedUserId, UserAvoidRelation::getReviewId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L, 10L),
                        org.assertj.core.groups.Tuple.tuple(2L, 1L, 10L));
    }
}
