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
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
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
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;

    // 위치 업데이트 시 반경 안/밖 여부를 계산할 때 사용할 기준 반경
    // 발표회 라이브 시연을 위해 만남 장소 반경을 250km로 확장
    private static final double LOCATION_TRACKING_RADIUS_METERS = MeetVerificationPolicy.MEETING_RADIUS_METERS;

    // 내 위치 업데이트
    @Override
    @Transactional
    public UpdateLocationResponseDto updateMyLocation(Long matchId, Long userId, UpdateLocationRequestDto requestDto) {

        // 매칭 정보 조회
        MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);

        // 위치 공유가 가능한 매칭 상태인지 검증
        validateTrackableMatch(matchInfo);

        // 게시글 정보 조회
        PostInfoDto postInfo = postInternalService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        boolean isAuthor = userId.equals(postInfo.authorId());
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfo.postId());
        UserLocation primaryLocation = null;

        if (isAuthor) {
            // 단체 매칭 등록자는 같은 게시글의 모든 활성 matchId에 위치를 동기화해,
            // 각 신청자 화면에서도 등록자 위치 노출
            for (Long activeMatchId : activeMatchIds) {

                UserLocation savedLocation = upsertLocation(
                        activeMatchId,
                        userId,
                        requestDto,
                        postInfo.placeLat(),
                        postInfo.placeLng()
                );

                if (activeMatchId.equals(matchId)) {
                    primaryLocation = savedLocation;
                }
            }
            if (primaryLocation == null) {

                primaryLocation = upsertLocation(
                        matchId,
                        userId,
                        requestDto,
                        postInfo.placeLat(),
                        postInfo.placeLng()
                );
            }
        } else {
            primaryLocation = upsertLocation(
                    matchId,
                    userId,
                    requestDto,
                    postInfo.placeLat(),
                    postInfo.placeLng()
            );
        }

        return UpdateLocationResponseDto.from(primaryLocation);
    }

    // 양측 위치 조회
    @Override
    public GetLocationResponseDto getLocations(Long matchId, Long userId) {

        // 매칭 정보 조회
        MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);

        // 위치 공유가 가능한 매칭 상태인지 검증
        validateTrackableMatch(matchInfo);

        // 게시글 정보 조회
        PostInfoDto postInfo = postInternalService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfo.postId());
        Map<Long, MatchInfoDto> activeMatchMap = matchInternalService.getMatchInfos(activeMatchIds);
        Set<Long> activeApplicantIds = activeMatchMap.values()
                .stream()
                .map(MatchInfoDto::applicantId)
                .collect(Collectors.toSet());

        if (!userId.equals(postInfo.authorId()) && !activeApplicantIds.contains(userId)) {
            throw new LocationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 같은 게시글의 활성 matchId 전체 조회 및 단체 매칭 참여자 위치 구성
        List<UserLocation> locations = userLocationRepository.findAllByMatchIdIn(activeMatchIds);

        // 내 위치 — role은 내가 누구냐에 따라 결정
        boolean isAuthor = userId.equals(postInfo.authorId());
        LocationDto myLocation = locations.stream()
                .filter(loc -> loc.getUserId().equals(userId))
                .findFirst()
                .map(loc -> LocationDto.from(loc, isAuthor ? LocationRole.AUTHOR : LocationRole.APPLICANT))
                .orElse(null);

        // 개인정보 보호를 위해 시연용 반경 250km 안에 들어온 상대방 위치만 노출
        List<LocationDto> opponentLocations = locations.stream()
                .filter(loc -> !loc.getUserId().equals(userId))
                .filter(loc -> loc.getUserId().equals(postInfo.authorId()) || activeApplicantIds.contains(loc.getUserId()))
                .filter(UserLocation::isInRange)
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

    private UserLocation upsertLocation(
            Long matchId,
            Long userId,
            UpdateLocationRequestDto requestDto,
            BigDecimal placeLat,
            BigDecimal placeLng
    ) {
        // 최신 위치가 약속 장소 반경 안인지 서버에서 직접 계산
        // 프론트 값을 믿지 않고 서버 기준으로 isInRange / leftRangeAt을 관리하기 위함
        boolean inRange = isWithinTrackingRadius(
                requestDto.getLatitude(),
                requestDto.getLongitude(),
                placeLat,
                placeLng
        );

        Optional<UserLocation> exist = userLocationRepository.findByMatchIdAndUserId(matchId, userId);

        if (exist.isPresent()) {
            UserLocation userLocation = exist.get();

            // 기존 위치를 갱신하면서 반경 안/밖 전환 시각도 함께 갱신
            userLocation.updateLocation(
                    requestDto.getLatitude(),
                    requestDto.getLongitude(),
                    inRange
            );

            return userLocation;
        }

        // 최초 위치 저장 시에도 현재 반경 안/밖 여부를 함께 저장
        UserLocation userLocation = UserLocation.builder()
                .matchId(matchId)
                .userId(userId)
                .latitude(requestDto.getLatitude())
                .longitude(requestDto.getLongitude())
                .isInRange(inRange)
                .build();

        return userLocationRepository.save(userLocation);
    }

    // 먼저 나간사용자 판단 메서드
    @Override
    public Optional<Long> findFirstLeftUserId(Long matchId, Long authorId, Long applicantId) {

        // 등록자 위치 데이터 조회
        Optional<UserLocation> authorLocationOpt =
                userLocationRepository.findByMatchIdAndUserId(matchId, authorId);

        // 신청자 위치 데이터 조회
        Optional<UserLocation> applicantLocationOpt =
                userLocationRepository.findByMatchIdAndUserId(matchId, applicantId);

        // 둘 중 하나라도 위치 데이터가 없으면 먼저 벗어난 사람을 판단할 수 없음
        if (authorLocationOpt.isEmpty() || applicantLocationOpt.isEmpty()) {
            return Optional.empty();
        }

        UserLocation authorLocation = authorLocationOpt.get();
        UserLocation applicantLocation = applicantLocationOpt.get();

        // leftRangeAt이 있으면 그 값을 사용
        // leftRangeAt이 없는 경우는 앱 종료 등으로 "반경 안 → 밖" 전환을 못 잡은 케이스일 수 있으므로
        // 마지막 반경 안 체류 시각(lastInRangeAt)을 보조 기준으로 사용
        LocalDateTime authorLeftAt = authorLocation.getLeftRangeAt() != null
                ? authorLocation.getLeftRangeAt()
                : authorLocation.getLastInRangeAt();

        LocalDateTime applicantLeftAt = applicantLocation.getLeftRangeAt() != null
                ? applicantLocation.getLeftRangeAt()
                : applicantLocation.getLastInRangeAt();


        // 양쪽 모두 기준 시각이 없으면 판단 불가.
        if (authorLeftAt == null && applicantLeftAt == null) {
            return Optional.empty();
        }

        // 한쪽만 기준 시각이 없는 경우도 데이터가 불완전한 상태이므로 임의 판정하지 않는다.
        if (authorLeftAt == null || applicantLeftAt == null) {
            return Optional.empty();
        }

        // 등록자가 더 이른 시각에 벗어났으면 등록자 노쇼
        if (authorLeftAt.isBefore(applicantLeftAt)) {
            return Optional.of(authorId);
        }

        // 신청자가 더 이른 시각에 벗어났으면 신청자 노쇼
        if (applicantLeftAt.isBefore(authorLeftAt)) {
            return Optional.of(applicantId);
        }

        // 시각이 완전히 같으면 판단 불가
        return Optional.empty();
    }

    // 현재 위치가 약속 장소 반경 안인지 계산
    private boolean isWithinTrackingRadius(
            BigDecimal currentLat,
            BigDecimal currentLng,
            BigDecimal placeLat,
            BigDecimal placeLng
    ) {
        double distance = GpsUtils.calculateDistance(
                currentLat.doubleValue(),
                currentLng.doubleValue(),
                placeLat.doubleValue(),
                placeLng.doubleValue()
        );

        return distance <= LOCATION_TRACKING_RADIUS_METERS;
    }

    private List<Long> getActiveMatchIdsByPostId(Long postId) {
        List<Long> matchIds = matchInternalService.getMatchIdsByPostId(postId);
        Map<Long, MatchInfoDto> matchInfoMap = matchInternalService.getMatchInfos(matchIds);

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
