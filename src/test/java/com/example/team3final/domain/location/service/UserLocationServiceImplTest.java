package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLocationServiceImplTest {

    @Mock
    private UserLocationRepository userLocationRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PostService postService;

    @InjectMocks
    private UserLocationServiceImpl userLocationService;

    @Test
    @DisplayName("내 위치 업데이트 - 신규 저장")
    void updateMyLocation_New_Success() {
        // given
        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchInfo.isParticipant(1L, 100L)).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);

        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(postInfo);

        given(userLocationRepository.findByMatchIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        UpdateLocationRequestDto request = UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal("37.5"))
                .longitude(new BigDecimal("127.0"))
                .build();

        // when
        UpdateLocationResponseDto result = userLocationService.updateMyLocation(1L, 1L, request);

        // then
        assertNotNull(result);
        verify(userLocationRepository).save(any(UserLocation.class));
    }

    @Test
    @DisplayName("내 위치 업데이트 - 성공 (기존 업데이트)")
    void updateMyLocation_Existing_Success() {
        // given
        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchInfo.isParticipant(1L, 100L)).willReturn(true);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);

        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(postInfo);

        UserLocation userLocation = mock(UserLocation.class);
        given(userLocationRepository.findByMatchIdAndUserId(1L, 1L)).willReturn(Optional.of(userLocation));

        UpdateLocationRequestDto request = UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal("37.5"))
                .longitude(new BigDecimal("127.0"))
                .build();

        // when
        UpdateLocationResponseDto result = userLocationService.updateMyLocation(1L, 1L, request);

        // then
        assertNotNull(result);
        verify(userLocation).updateLocation(new BigDecimal("37.5"), new BigDecimal("127.0"));
    }

    @Test
    @DisplayName("내 위치 업데이트 - 실패 (당사자 아님)")
    void updateMyLocation_Fail_NotParticipant() {
        // given
        MatchInfoDto matchInfo = mock(MatchInfoDto.class);
        given(matchInfo.postId()).willReturn(10L);
        given(matchInfo.isParticipant(1L, 100L)).willReturn(false);
        given(matchService.getMatchInfo(1L)).willReturn(matchInfo);
        PostInfoDto postInfo = mock(PostInfoDto.class);
        given(postInfo.authorId()).willReturn(100L);
        given(postService.getPostInfo(10L)).willReturn(postInfo);

        UpdateLocationRequestDto request = UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal("37.5"))
                .longitude(new BigDecimal("127.0"))
                .build();

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.LocationException.class, () -> {
            userLocationService.updateMyLocation(1L, 1L, request);
        });
    }
}
