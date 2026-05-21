package com.example.team3final.domain.location.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.LocationException;
import com.example.team3final.domain.location.dto.LocationDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
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
    private final MatchService matchQueryService;
    private final PostService postQueryService;

    // 내 위치 업데이트
    @Override
    @Transactional
    public UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto) {

        // 매칭 정보 조회
        MatchInfoDto matchInfo = matchQueryService.getMatchInfo(matchId);

        // 게시글 정보 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

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

        // 매칭 정보 조회
        MatchInfoDto matchInfo = matchQueryService.getMatchInfo(matchId);

        // 게시글 정보 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // matchId로 양측 위치 전체 조회
        List<UserLocation> locations = userLocationRepository.findAllByMatchId(matchId);

        // 내 위치와 상대방 위치 분리
        // 내 위치 -> userId가 일치하는 것
        LocationDto myLocation = locations.stream()
                .filter(loc -> loc.getUserId().equals(userId))
                .findFirst()
                .map(LocationDto::from)
                .orElse(null); // 아직 위치를 한 번도 업데이트 안했으면 null

        // 상대방 위치 -> userId가 불일치
        LocationDto opponentLocation = locations.stream()
                .filter(loc -> !loc.getUserId().equals(userId))
                .findFirst()
                .map(LocationDto::from)
                .orElse(null); // 상대방이 아직 위치를 보내지 않았으면 null

        return GetLocationResponseDto.of(myLocation, opponentLocation);
    }

    // 매칭 종료 시 위치 데이터 삭제
    @Override
    @Transactional
    public void deleteLocationsByMatchId(Long matchId) {
        userLocationRepository.deleteAllByMatchId(matchId);
    }
}
