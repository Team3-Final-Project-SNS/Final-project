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
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLocationServiceImpl implements UserLocationService {

    private final UserLocationRepository userLocationRepository;
    private final MatchService matchQueryService;
    private final PostService postQueryService;

    // 반경 60m (GPS 오차 범위 포함)
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;

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

        // 내가 등록자인지 신청자인지 판단 → role 결정에 사용
        boolean isAuthor = userId.equals(postInfo.authorId());

        // matchId로 양측 위치 전체 조회
        List<UserLocation> locations = userLocationRepository.findAllByMatchId(matchId);

        // 내 위치 — role은 내가 누구냐에 따라 결정
        LocationDto myLocation = locations.stream()
                .filter(loc -> loc.getUserId().equals(userId))
                .findFirst()
                .map(loc -> LocationDto.from(loc, isAuthor ? LocationRole.AUTHOR : LocationRole.APPLICANT))
                .orElse(null);

        // 상대방 위치: 약속 장소 반경 안에 있을 때만 노출
        UserLocation opponentRaw = locations.stream()
                .filter(loc -> !loc.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        // if문을 안타는 조건을 대비하여, null값으로 초기화
        LocationDto opponentLocation = null;

        if (opponentRaw != null) {
            // GpsUtils로 상대방과 약속 장소 간 거리 계산
            double distance = GpsUtils.calculateDistance(
                    opponentRaw.getLatitude().doubleValue(),
                    opponentRaw.getLongitude().doubleValue(),
                    postInfo.placeLat().doubleValue(),
                    postInfo.placeLng().doubleValue()
            );

            // 반경(60m) 안에 있을 때만 상대방 위치 노출
            // 반경 밖이면 null 반환 → 프론트에서 마커 미표시
            if (distance <= PLACE_VERIFICATION_RADIUS_METERS) {
                LocationRole opponentRole = isAuthor ? LocationRole.APPLICANT : LocationRole.AUTHOR;
                opponentLocation = LocationDto.from(opponentRaw, opponentRole);
            }
        }

        return GetLocationResponseDto.of(myLocation, opponentLocation);
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
}

