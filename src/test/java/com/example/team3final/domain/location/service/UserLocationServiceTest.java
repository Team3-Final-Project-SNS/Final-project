package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLocationServiceTest {

    @InjectMocks
    private UserLocationServiceImpl userLocationService;

    @Mock
    private UserLocationRepository userLocationRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;

    @Test
    @DisplayName("먼저 나간 유저 찾기 - 데이터 없음")
    void findFirstLeftUserId_NoData() {
        // given
        given(userLocationRepository.findByMatchIdAndUserId(anyLong(), anyLong())).willReturn(Optional.empty());

        // when
        Optional<Long> result = userLocationService.findFirstLeftUserId(1L, 10L, 20L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("내 위치 업데이트 - 성공")
    void updateMyLocation_Success() {
        MatchInfoDto matchInfo = new MatchInfoDto(1L, 100L, 20L, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(100L, 10L, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusHours(1));
        UpdateLocationRequestDto request = UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal("37.0"))
                .longitude(new BigDecimal("127.0"))
                .build();
        UserLocation savedLocation = UserLocation.builder()
                .matchId(1L)
                .userId(20L)
                .latitude(new BigDecimal("37.0"))
                .longitude(new BigDecimal("127.0"))
                .isInRange(true)
                .build();
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        given(postService.getPostInfo(100L)).willReturn(postInfo);
        given(userLocationRepository.findByMatchIdAndUserId(1L, 20L)).willReturn(Optional.empty());
        given(userLocationRepository.save(any(UserLocation.class))).willReturn(savedLocation);

        UpdateLocationResponseDto result = userLocationService.updateMyLocation(1L, 20L, request);

        assertThat(result).isNotNull();
        verify(userLocationRepository).save(any(UserLocation.class));
    }

    @Test
    @DisplayName("위치 목록 조회 - 성공")
    void getLocations_Success() {
        MatchInfoDto matchInfo = new MatchInfoDto(1L, 100L, 20L, MatchStatus.MATCHED);
        PostInfoDto postInfo = new PostInfoDto(100L, 10L, new BigDecimal("37.0"), new BigDecimal("127.0"), LocalDateTime.now().plusHours(1));
        UserLocation authorLocation = UserLocation.builder()
                .matchId(1L)
                .userId(10L)
                .latitude(new BigDecimal("37.0"))
                .longitude(new BigDecimal("127.0"))
                .isInRange(true)
                .build();
        UserLocation applicantLocation = UserLocation.builder()
                .matchId(1L)
                .userId(20L)
                .latitude(new BigDecimal("37.0"))
                .longitude(new BigDecimal("127.0"))
                .isInRange(true)
                .build();
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        given(postService.getPostInfo(100L)).willReturn(postInfo);
        given(matchService.getMatchIdsByPostId(100L)).willReturn(List.of(1L));
        given(matchService.getMatchInfos(List.of(1L))).willReturn(Map.of(1L, matchInfo));
        given(userLocationRepository.findAllByMatchIdIn(List.of(1L))).willReturn(List.of(authorLocation, applicantLocation));

        GetLocationResponseDto result = userLocationService.getLocations(1L, 20L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("최신 위치 반경 확인 - 성공")
    void isFreshLocationWithinRadius_Success() {
        UserLocation location = UserLocation.builder()
                .matchId(1L)
                .userId(20L)
                .latitude(new BigDecimal("37.0"))
                .longitude(new BigDecimal("127.0"))
                .isInRange(true)
                .build();
        given(userLocationRepository.findByMatchIdAndUserId(1L, 20L)).willReturn(Optional.of(location));

        boolean result = userLocationService.isFreshLocationWithinRadius(
                1L,
                20L,
                new BigDecimal("37.0"),
                new BigDecimal("127.0"),
                60.0,
                10
        );

        assertThat(result).isTrue();
    }
}
