package com.example.team3final.domain.location.service;

import com.example.team3final.common.exception.LocationException;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 위치 서비스 단위 테스트")
class UserLocationServiceTest {

    @Mock
    private UserLocationRepository userLocationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private PostInternalService postInternalService;

    @InjectMocks
    private UserLocationServiceImpl userLocationService;

    @Test
    @DisplayName("신청자 위치 업데이트는 매치 참여자 검증 후 새 위치를 저장한다")
    void updateMyLocation_shouldSaveApplicantLocation() {
        when(matchInternalService.getMatchInfo(10L)).thenReturn(matchInfo(10L, 100L, 2L, MatchStatus.MATCHED));
        when(postInternalService.getPostInfo(100L)).thenReturn(postInfo(100L, 1L));
        when(matchInternalService.getMatchIdsByPostId(100L)).thenReturn(List.of(10L));
        when(matchInternalService.getMatchInfos(List.of(10L))).thenReturn(Map.of(10L, matchInfo(10L, 100L, 2L, MatchStatus.MATCHED)));
        when(userLocationRepository.findByMatchIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(userLocationRepository.save(any(UserLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLocationResponseDto result = userLocationService.updateMyLocation(10L, 2L, locationRequest("37.5665", "126.9780"));

        assertThat(result.matchId()).isEqualTo(10L);
        assertThat(result.userId()).isEqualTo(2L);
        verify(userLocationRepository).save(any(UserLocation.class));
    }

    @Test
    @DisplayName("위치 업데이트는 매치 상태가 MATCHED가 아니면 위치 예외를 던진다")
    void updateMyLocation_shouldThrowWhenMatchNotTrackable() {
        when(matchInternalService.getMatchInfo(10L)).thenReturn(matchInfo(10L, 100L, 2L, MatchStatus.COMPLETED));

        assertThatThrownBy(() -> userLocationService.updateMyLocation(10L, 2L, locationRequest("37.5665", "126.9780")))
                .isInstanceOf(LocationException.class);
    }

    @Test
    @DisplayName("위치 조회는 내 위치와 반경 안의 상대 위치 목록을 반환한다")
    void getLocations_shouldReturnMyAndOpponentLocations() {
        UserLocation authorLocation = userLocation(10L, 1L, "37.5665", "126.9780", true);
        UserLocation applicantLocation = userLocation(10L, 2L, "37.5666", "126.9781", true);
        when(matchInternalService.getMatchInfo(10L)).thenReturn(matchInfo(10L, 100L, 2L, MatchStatus.MATCHED));
        when(postInternalService.getPostInfo(100L)).thenReturn(postInfo(100L, 1L));
        when(matchInternalService.getMatchIdsByPostId(100L)).thenReturn(List.of(10L));
        when(matchInternalService.getMatchInfos(List.of(10L))).thenReturn(Map.of(10L, matchInfo(10L, 100L, 2L, MatchStatus.MATCHED)));
        when(userLocationRepository.findAllByMatchIdIn(List.of(10L))).thenReturn(List.of(authorLocation, applicantLocation));

        GetLocationResponseDto result = userLocationService.getLocations(10L, 2L);

        assertThat(result.myLocation()).isNotNull();
        assertThat(result.opponentLocations()).hasSize(1);
    }

    @Test
    @DisplayName("신선한 위치가 지정 반경 안에 있으면 true를 반환한다")
    void isFreshLocationWithinRadius_shouldReturnTrueWhenFreshAndInRadius() {
        UserLocation location = userLocation(10L, 1L, "37.5665", "126.9780", true);
        ReflectionTestUtils.setField(location, "updatedAt", LocalDateTime.now());
        when(userLocationRepository.findByMatchIdAndUserId(10L, 1L)).thenReturn(Optional.of(location));

        boolean result = userLocationService.isFreshLocationWithinRadius(
                10L, 1L, new BigDecimal("37.5665"), new BigDecimal("126.9780"), 10, 60);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("두 사용자 중 더 먼저 반경을 벗어난 사용자 ID를 반환한다")
    void findFirstLeftUserId_shouldReturnEarlierLeftUserId() {
        UserLocation authorLocation = userLocation(10L, 1L, "37.5665", "126.9780", true);
        UserLocation applicantLocation = userLocation(10L, 2L, "37.5666", "126.9781", true);
        ReflectionTestUtils.setField(authorLocation, "leftRangeAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        ReflectionTestUtils.setField(applicantLocation, "leftRangeAt", LocalDateTime.of(2026, 1, 1, 12, 1));
        when(userLocationRepository.findByMatchIdAndUserId(10L, 1L)).thenReturn(Optional.of(authorLocation));
        when(userLocationRepository.findByMatchIdAndUserId(10L, 2L)).thenReturn(Optional.of(applicantLocation));

        Optional<Long> result = userLocationService.findFirstLeftUserId(10L, 1L, 2L);

        assertThat(result).contains(1L);
    }

    private UpdateLocationRequestDto locationRequest(String latitude, String longitude) {
        return UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .build();
    }

    private UserLocation userLocation(Long matchId, Long userId, String latitude, String longitude, boolean isInRange) {
        return UserLocation.builder()
                .matchId(matchId)
                .userId(userId)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .isInRange(isInRange)
                .build();
    }

    private MatchInfoDto matchInfo(Long matchId, Long postId, Long applicantId, MatchStatus status) {
        return new MatchInfoDto(matchId, postId, applicantId, status);
    }

    private PostInfoDto postInfo(Long postId, Long authorId) {
        return new PostInfoDto(
                postId,
                authorId,
                new BigDecimal("37.5665"),
                new BigDecimal("126.9780"),
                LocalDateTime.of(2026, 1, 1, 12, 0));
    }
}
