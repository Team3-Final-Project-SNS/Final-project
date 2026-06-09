package com.example.team3final.domain.location.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.LocationException;
import com.example.team3final.common.utils.GpsUtils;
import com.example.team3final.domain.location.dto.LocationDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.enums.LocationRole;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        // 위치 공유가 가능한 매칭 상태인지 검증
        validateTrackableMatch(matchInfo);

        // 게시글 정보 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        boolean isAuthor = userId.equals(postInfo.authorId());
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfo.postId());
        UserLocation primaryLocation = null;

        if (isAuthor) {
            // 단체 매칭 등록자는 같은 게시글의 모든 활성 matchId에 위치를 동기화해,
            // 각 신청자 화면에서도 등록자 위치가 보이도록 합니다.
            for (Long activeMatchId : activeMatchIds) {
                UserLocation savedLocation = upsertLocation(activeMatchId, userId, requestDto);
                if (activeMatchId.equals(matchId)) {
                    primaryLocation = savedLocation;
                }
            }
            if (primaryLocation == null) {
                primaryLocation = upsertLocation(matchId, userId, requestDto);
            }
        } else {
            primaryLocation = upsertLocation(matchId, userId, requestDto);
        }

        return UpdateLocationResponseDto.from(primaryLocation);
    }

    // 양측 위치 조회
    @Override
    public GetLocationResponseDto getLocations(Long matchId, Long userId) {

        // 매칭 정보 조회
        MatchInfoDto matchInfo = matchQueryService.getMatchInfo(matchId);

        // 위치 공유가 가능한 매칭 상태인지 검증
        validateTrackableMatch(matchInfo);

        // 게시글 정보 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfo.postId());
        Map<Long, MatchInfoDto> activeMatchMap = matchQueryService.getMatchInfos(activeMatchIds);
        Set<Long> activeApplicantIds = activeMatchMap.values()
                .stream()
                .map(MatchInfoDto::applicantId)
                .collect(Collectors.toSet());

        if (!userId.equals(postInfo.authorId()) && !activeApplicantIds.contains(userId)) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 같은 게시글의 활성 matchId 전체를 조회해 단체 매칭 참여자 위치를 한 번에 구성합니다.
        List<UserLocation> locations = userLocationRepository.findAllByMatchIdIn(activeMatchIds);

        // 내 위치 — role은 내가 누구냐에 따라 결정
        boolean isAuthor = userId.equals(postInfo.authorId());
        LocationDto myLocation = locations.stream()
                .filter(loc -> loc.getUserId().equals(userId))
                .findFirst()
                .map(loc -> LocationDto.from(loc, isAuthor ? LocationRole.AUTHOR : LocationRole.APPLICANT))
                .orElse(null);

        // 상대방 위치는 반경 조건으로 숨기지 않고, 같은 게시글의 활성 참여자 위치를 모두 노출합니다.
        List<LocationDto> opponentLocations = locations.stream()
                .filter(loc -> !loc.getUserId().equals(userId))
                .filter(loc -> loc.getUserId().equals(postInfo.authorId()) || activeApplicantIds.contains(loc.getUserId()))
                .collect(Collectors.toMap(
                        UserLocation::getUserId,
                        Function.identity(),
                        (first, second) -> first
                ))
                .values()
                .stream()
                .map(loc -> LocationDto.from(
                        loc,
                        loc.getUserId().equals(postInfo.authorId()) ? LocationRole.AUTHOR : LocationRole.APPLICANT
                ))
                .toList();

        return GetLocationResponseDto.of(myLocation, opponentLocations);
    }

    private UserLocation upsertLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto) {
        Optional<UserLocation> exist = userLocationRepository.findByMatchIdAndUserId(matchId, userId);

        if (exist.isPresent()) {
            UserLocation userLocation = exist.get();
            userLocation.updateLocation(requestDto.getLatitude(), requestDto.getLongitude());
            return userLocation;
        }

        UserLocation userLocation = UserLocation.builder()
                .matchId(matchId)
                .userId(userId)
                .latitude(requestDto.getLatitude())
                .longitude(requestDto.getLongitude())
                .build();
        return userLocationRepository.save(userLocation);
    }

    private List<Long> getActiveMatchIdsByPostId(Long postId) {
        List<Long> matchIds = matchQueryService.getMatchIdsByPostId(postId);
        Map<Long, MatchInfoDto> matchInfoMap = matchQueryService.getMatchInfos(matchIds);

        List<Long> activeMatchIds = matchInfoMap.values()
                .stream()
                .filter(match -> match.status() == MatchStatus.MATCHED)
                .map(MatchInfoDto::matchId)
                .toList();

        return activeMatchIds.isEmpty() ? List.of() : activeMatchIds;
    }

    // QR 만료 시 노쇼 판정을 위해 호출되는 메서드
    // "이 유저가 지금도 약속 장소 반경 안에 있는가?"를 판단
    @Override
    public boolean isFreshLocationWithinRadius(
            Long matchId,
            Long userId,
            BigDecimal placeLat,
            BigDecimal placeLng,
            double radiusMeters,
            long freshnessSeconds
    ) {
        return userLocationRepository.findByMatchIdAndUserId(matchId, userId)
                // 현재 시각 - freshnessSeconds 이후에 업데이트된 위치만 통과
                .filter(location -> !location.getUpdatedAt()
                        .isBefore(LocalDateTime.now().minusSeconds(freshnessSeconds)))
                // 약속 장소로부터 radiusMeters 이내인 위치만 통과
                .filter(location -> {
                    double distance = GpsUtils.calculateDistance(
                            location.getLatitude().doubleValue(),
                            location.getLongitude().doubleValue(),
                            placeLat.doubleValue(),
                            placeLng.doubleValue()
                    );
                    return distance <= radiusMeters;
                })
                .isPresent(); // 두 필터를 모두 통과한 위치가 있으면 true
    }

    // 위치 공유/조회가 가능한 매칭 상태인지 검증하는 공통 메서드
    private void validateTrackableMatch(MatchInfoDto matchInfoDto) {

        // matchInfo.status()는 현재 매칭 상태
        // 위치 공유는 실제 약속 진행 중인 MATCHED 상태에서만 의미 있음
        if (matchInfoDto.status() != MatchStatus.MATCHED) {
            // MATCHED가 아니면 위치 업데이트/조회 차단
            throw new LocationException(ErrorCode.LOCATION_NOT_TRACKABLE);
        }
    }
}
