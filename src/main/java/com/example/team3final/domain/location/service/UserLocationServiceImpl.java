package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.LocationDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLocationServiceImpl implements UserLocationService {

    private final UserLocationRepository userLocationRepository;
    private final MatchQueryService matchQueryService;

    // 내 위치 업데이트
    @Override
    @Transactional
    public UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto) {

        // Match 조회
        // TODO: Post 도메인에서 Dto 완성 후 authorId 실제 조회로 적용
        Match match = matchQueryService.getMatchById(matchId);

        // 기존 위치 조회
        Optional<UserLocation> exist = userLocationRepository.findByMatchIdAndUserId(matchId, userId);

        UserLocation userLocation;

        if (exist.isPresent()) {
            // 있으면 UPDATE - 더티 체킹으로 자동 저장
            userLocation = exist.get();
            userLocation.updateLocation(requestDto.getLatitude(), requestDto.getLongitude());
        } else {
            // 없으면 INSERT
            userLocation = UserLocation.builder()
                    .matchId(matchId)
                    .userId(userId)
                    .latitude(requestDto.getLatitude())
                    .longitude(requestDto.getLongitude())
                    .build();
            userLocationRepository.save(userLocation);
        }

        return UpdateLocationResponseDto.from(userLocation);
    }

    // 양측 위치 조회
    @Override
    public GetLocationResponseDto getLocations(Long matchId, Long userId) {

        // Match 조회
        // TODO: Post Dto 완성 후 authorId 실제 조회로 교체
        matchQueryService.getMatchById(matchId);

        // matchId로 양측 위치 전체 조회
        List<UserLocation> locations = userLocationRepository.findAllByMatchId(matchId);

        // 내 위치와 상대방 위치 분리
        LocationDto myLocation = locations.stream()
                .filter(loc -> loc.getUserId().equals(userId))
                .findFirst()
                .map(LocationDto::from)
                .orElse(null);

        LocationDto opponentLocation = locations.stream()
                .filter(loc -> !loc.getUserId().equals(userId))
                .findFirst()
                .map(LocationDto::from)
                .orElse(null);

        return GetLocationResponseDto.of(myLocation, opponentLocation);
    }

    // 매칭 종료 시 위치 데이터 삭제
    @Override
    @Transactional
    public void deleteLocationsByMatchId(Long matchId) {
        userLocationRepository.deleteAllByMatchId(matchId);
    }
}
