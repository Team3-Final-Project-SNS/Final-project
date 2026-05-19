package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
