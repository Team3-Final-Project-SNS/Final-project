package com.example.team3final.domain.review.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.common.exception.ReviewException;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetWrittenReviewsResponseDto;
import com.example.team3final.domain.review.dto.response.ReviewItemResponseDto;
import com.example.team3final.domain.review.entity.Review;
import com.example.team3final.domain.review.entity.ReviewBadTagEntity;
import com.example.team3final.domain.review.entity.ReviewGoodTagEntity;
import com.example.team3final.domain.review.enums.ReviewBadTag;
import com.example.team3final.domain.review.enums.ReviewGoodTag;
import com.example.team3final.domain.review.repository.ReviewBadTagRepository;
import com.example.team3final.domain.review.repository.ReviewGoodTagRepository;
import com.example.team3final.domain.review.repository.ReviewRepository;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserMannerService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



/**
 * 후기 작성과 조회 비즈니스 로직을 처리하는 서비스 구현체입니다.
 * Match, Post, User, Point 도메인과 서비스-to-서비스 방식으로 협력합니다.
 * Review 도메인은 매칭/게시글/유저 Repository를 직접 참조하지 않고,
 * 각 도메인 Service를 통해 필요한 정보만 조회합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /**
     * 후기 작성 가능 기간입니다.
     * 매칭 완료 시점으로부터 7일 이내에만 후기를 작성할 수 있습니다.
     */
    private static final int REVIEW_WRITE_DEADLINE_DAYS = 7;

    /**
     * 기본 매너 온도입니다.
     * 후기가 없거나 평균 점수 변화량이 0이면 36.5도를 기준으로 유지합니다.
     */
    private static final BigDecimal BASE_MANNER_TEMPERATURE = new BigDecimal("36.5");


    /**
     * 가중치 수치 설정 (익명성 보장, 역산 방지)
     */
    private static final BigDecimal MANNER_WEIGHT = new BigDecimal("0.1592");

    /**
     * 매너 온도 하한선입니다.
     */
    private static final BigDecimal MIN_MANNER_TEMPERATURE = BigDecimal.ZERO;

    /**
     * 매너 온도 상한선입니다.
     */
    private static final BigDecimal MAX_MANNER_TEMPERATURE = new BigDecimal("99.0");

    /**
     * 만남(리뷰 합산)으로 변화할 수 있는 '온도 변동치'의 상한/하한선
     */
    private static final BigDecimal MAX_TEMPERATURE_DELTA_LIMIT = new BigDecimal("1.6");
    private static final BigDecimal MIN_TEMPERATURE_DELTA_LIMIT = new BigDecimal("-1.6");

    private final NotificationPublisher notificationPublisher;
    private final ReviewRepository reviewRepository;
    private final ReviewGoodTagRepository reviewGoodTagRepository;
    private final ReviewBadTagRepository reviewBadTagRepository;
    private final ReviewAvoidanceService reviewAvoidanceService;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final UserMannerService userMannerService;
    private final UserInternalService userInternalService;
    private final UserPointService userPointService;


    /**
     * 후기를 생성합니다.
     * 처리 흐름:
     * 1. 매칭/게시글 정보 조회
     * 2. 작성자가 신청자인지 검증
     * 3. 매칭 완료 상태와 작성 가능 기간 검증
     * 4. 중복 후기 작성 여부 검증
     * 5. 기존 만남 리뷰 평균 점수 조회
     * 6. 태그 점수 계산 후 Review 저장
     * 7. 선택 태그 상세 저장
     * 8. 후기 작성 보상 포인트 지급
     * 9. 만남 리뷰 평균 변화량만 등록자 매너온도에 반영
     */
    @Override
    @Transactional
    public CreateReviewResponseDto createReview(
            Long matchId,
            Long writerId,
            CreateReviewRequestDto request
    ) {
        Match match = matchInternalService.getMatchById(matchId);
        PostMatchInfoDto post = postInternalService.getPostMatchInfo(match.getPostId());

        // 1차 방어: 애플리케이션 레벨 중복 체크
        validateReviewCreatable(match, post, writerId);

        List<ReviewGoodTag> goodTags = distinct(request.goodTags());
        List<ReviewBadTag> badTags = distinct(request.badTags());
        validateTags(goodTags, badTags);

        Long authorId = post.authorId();
        List<Long> postMatchIds = matchInternalService.getMatchIdsByPostId(post.postId());
        BigDecimal previousMeetingAverageScore = calculateMeetingAverageScore(postMatchIds);
        int tagScoreDelta = calculateTagScoreDelta(goodTags, badTags);

        // 2차 방어: DB UNIQUE 제약 (match_id, writer_id)
        // 동시 요청이 1차 체크를 둘 다 통과해도 하나만 저장됨
        // DataIntegrityViolationException → REVIEW_ALREADY_EXISTS로 변환하여 500 방지
        Review review;
        try {
            review = reviewRepository.save(
                    Review.builder()
                            .matchId(match.getId())
                            .writerId(writerId)
                            .tagScoreDelta(tagScoreDelta)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 = 동시 요청에서 이미 다른 스레드가 먼저 저장한 케이스
            throw new ReviewException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        saveGoodTags(review.getId(), goodTags);
        saveBadTags(review.getId(), badTags);

        userPointService.rewardReviewPoint(
                writerId,
                Review.REVIEW_REWARD_POINT,
                match.getId()
        );
        // 11. 후기 작성 포인트 지급 알림 - 후기 작성자에게
        notificationPublisher.sendReviewPoint(review.getWriterId(), review.getId());

        // 다시 만나고 싶지 않아요를 선택하면 양방향 회피 관계를 저장합니다.
        // 리뷰 대상은 만남 자체지만, 블라인드 정책은 신청자와 등록자 사이에 적용합니다.
        if (containsDoNotWantToMeetAgainTag(badTags)) {
            reviewAvoidanceService.createAvoidRelation(writerId, authorId, review.getId());
        }

        // 이번 만남으로 인한 평균 점수 변화량을 구합니다 (제한 없는 순수 평균).
        BigDecimal currentMeetingAverageScore = calculateMeetingAverageScore(postMatchIds);
        BigDecimal averageScoreDelta = currentMeetingAverageScore.subtract(previousMeetingAverageScore);

        // 변화량에 가중치(0.1592)를 곱해 '실제 변동할 온도 변동치'를 계산하고 소수점 첫째 자리에서 반올림합니다.
        BigDecimal temperatureDelta = averageScoreDelta.multiply(MANNER_WEIGHT)
                .setScale(1, RoundingMode.HALF_UP);

        // 이번 리뷰로 인해 오르내릴 '온도 변동치' 자체를 ±1.6도 범위로 가둡니다.
        if (temperatureDelta.compareTo(MAX_TEMPERATURE_DELTA_LIMIT) > 0) {
            temperatureDelta = MAX_TEMPERATURE_DELTA_LIMIT;
        } else if (temperatureDelta.compareTo(MIN_TEMPERATURE_DELTA_LIMIT) < 0) {
            temperatureDelta = MIN_TEMPERATURE_DELTA_LIMIT;
        }

        // 게시물 등록자의 기존 매너 온도를 가져와서 변동치(±1.6)를 더해줍니다.
        BigDecimal currentTemperature = userMannerService.getMannerTemperature(authorId);
        BigDecimal nextTemperature = currentTemperature.add(temperatureDelta);

        // 최종 온도가 시스템 전체 매너온도 범위(0도 ~ 99도)를 벗어나지 않도록 안전하게 가둡니다.
        BigDecimal finalTemperature = clampMannerTemperature(nextTemperature);

        // 비관적 락을 획득하여 최종 확정된 매너 온도(finalTemperature)를 DB에 반영합니다.
        // (이미 서비스 단에서 가중치 계산이 끝났으므로 가중치 파라미터 자리에는 BigDecimal.ONE을 전달합니다)
        userMannerService.updateMannerTemperatureWithLock(authorId, finalTemperature, BigDecimal.ONE);

        // 12. 매너 온도 상승 알림 - 후기로 인한 매너온도 반영자에게
        notificationPublisher.sendMannerTemperatureChanged(authorId);

        UserInfoDto targetInfo = userInternalService.getUserInfo(authorId);

        return CreateReviewResponseDto.of(
                review,
                authorId,
                targetInfo.nickname(),
                goodTags,
                badTags,
                containsDoNotWantToMeetAgainTag(badTags)
        );
    }


    /**
     * 로그인 사용자가 직접 작성한 후기 목록을 조회합니다.
     * 사용자는 받은 후기 목록을 볼 수 없고,
     * 본인이 작성한 후기만 매칭 결과 화면에서 다시 확인할 수 있습니다.
     */
    @Override
    public GetWrittenReviewsResponseDto getWrittenReviews(
            Long currentUserId
    ) {
        List<Review> reviews = reviewRepository.findAllByWriterIdOrderByCreatedAtDesc(currentUserId);
        Map<Long, List<ReviewGoodTag>> goodTagMap = getGoodTagMap(reviews);
        Map<Long, List<ReviewBadTag>> badTagMap = getBadTagMap(reviews);
        UserInfoDto currentUserInfo = userInternalService.getUserInfo(currentUserId);

        List<ReviewItemResponseDto> content = reviews.stream()
                .map(review -> {
                    List<ReviewGoodTag> goodTags = goodTagMap.getOrDefault(review.getId(), List.of());
                    List<ReviewBadTag> badTags = badTagMap.getOrDefault(review.getId(), List.of());

                    return ReviewItemResponseDto.of(
                            review,
                            currentUserInfo.nickname(),
                            goodTags,
                            badTags,
                            containsDoNotWantToMeetAgainTag(badTags)
                    );
                })
                .toList();

        return new GetWrittenReviewsResponseDto(
                currentUserId,
                currentUserInfo.nickname(),
                content
        );
    }

    @Override
    public List<Long> getAvoidedUserIds(Long userId) {
        return reviewAvoidanceService.getAvoidedUserIds(userId);
    }

    @Override
    public boolean existsAvoidRelation(Long userId, Long otherUserId) {
        return reviewAvoidanceService.existsAvoidRelation(userId, otherUserId);
    }


    /**
     * 후기 작성 가능 조건을 검증합니다.
     * 검증 항목:
     * - 작성자가 해당 매칭의 신청자인지
     * - 등록자가 후기를 작성하지 않는지
     * - 매칭 상태가 COMPLETED인지
     * - 매칭 완료 후 7일 이내인지
     * - 같은 매칭에 이미 후기를 작성하지 않았는지
     */
    private void validateReviewCreatable(Match match, PostMatchInfoDto post, Long writerId) {
        if (!match.isParticipant(writerId, post.authorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 리뷰는 신청자가 만남 자체를 평가하는 기능입니다.
        // 1:1/단체 모두 등록자는 리뷰를 작성할 수 없습니다.
        if (post.authorId().equals(writerId)) {
            throw new ReviewException(ErrorCode.REVIEW_AUTHOR_NOT_ALLOWED);
        }

        // 신청자끼리 서로를 리뷰하는 흐름은 허용하지 않습니다.
        // writerId는 반드시 현재 match의 applicantId여야 합니다.
        if (!match.isApplicant(writerId)) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        if (match.getStatus() != MatchStatus.COMPLETED) {
            throw new ReviewException(ErrorCode.REVIEW_NOT_COMPLETED_MATCH);
        }

        if (match.getCompletedAt() == null
                || LocalDateTime.now().isAfter(match.getCompletedAt().plusDays(REVIEW_WRITE_DEADLINE_DAYS))) {
            throw new ReviewException(ErrorCode.REVIEW_PERIOD_EXPIRED);
        }

        if (reviewRepository.existsByMatchIdAndWriterId(match.getId(), writerId)) {
            throw new ReviewException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }


    /**
     * 후기 태그 선택 조건을 검증합니다.
     * 좋아요 태그와 아쉬워요 태그는 동시에 선택할 수 없고,
     * 둘 중 하나는 반드시 선택해야 합니다.
     */
    private void validateTags(List<ReviewGoodTag> goodTags, List<ReviewBadTag> badTags) {
        boolean goodTagSelected = !goodTags.isEmpty();
        boolean badTagSelected = !badTags.isEmpty();

        if (goodTagSelected == badTagSelected) {
            throw new ReviewException(ErrorCode.REVIEW_INVALID_TAG);
        }
    }


    /**
     * 선택된 태그를 기반으로 점수 변화량을 계산합니다.
     * 좋아요 태그는 각 +1점, 아쉬워요 태그는 각 -1점으로 계산하되,
     * 상대방이 선택한 태그 개수를 역산할 수 없도록 한 만남당 최대/최소 점수 제한을 둡니다.
     */
    private int calculateTagScoreDelta(List<ReviewGoodTag> goodTags, List<ReviewBadTag> badTags) {
        int goodScore = goodTags.stream()
                .mapToInt(ReviewGoodTag::getScoreDelta) // 원래 로직 유지
                .sum();

        int badScore = badTags.stream()
                .mapToInt(ReviewBadTag::getScoreDelta) // 원래 로직 유지
                .sum();

        int totalRawScore = goodScore + badScore;

        // 익명성 보장을 위한 한 만남(식사)당 점수 제한 설정
        // 좋았어요를 5개 다 골라도 최대 2점, 아쉬웠어요를 다 골라도 최소 -3점
        int maxLimit = 2;
        int minLimit = -3;

        // 범위를 제한하여 리턴 (-3 ~ +2 사이의 값만 나옴)
        return Math.max(minLimit, Math.min(maxLimit, totalRawScore));
    }


    /**
     * 좋아요 태그 선택 내역을 저장합니다.
     */
    private void saveGoodTags(Long reviewId, List<ReviewGoodTag> goodTags) {
        List<ReviewGoodTagEntity> entities = goodTags.stream()
                .map(tag -> ReviewGoodTagEntity.builder()
                        .reviewId(reviewId)
                        .tag(tag)
                        .build())
                .toList();

        reviewGoodTagRepository.saveAll(entities);
    }


    /**
     * 아쉬워요 태그 선택 내역을 저장합니다.
     */
    private void saveBadTags(Long reviewId, List<ReviewBadTag> badTags) {
        List<ReviewBadTagEntity> entities = badTags.stream()
                .map(tag -> ReviewBadTagEntity.builder()
                        .reviewId(reviewId)
                        .tag(tag)
                        .build())
                .toList();

        reviewBadTagRepository.saveAll(entities);
    }

    /**
     * 한 만남에 작성된 리뷰들의 평균 점수를 계산합니다.
     * 1:1 만남은 리뷰가 1개이므로 해당 리뷰 점수가 평균이 됩니다.
     * 단체 만남은 여러 신청자의 리뷰 점수를 평균 내어 등록자의 매너온도에 반영합니다.
     */
    private BigDecimal calculateMeetingAverageScore(List<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Review> reviews = reviewRepository.findAllByMatchIdIn(matchIds);
        if (reviews.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalScore = reviews.stream()
                .map(review -> BigDecimal.valueOf(review.getTagScoreDelta()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalScore.divide(
                BigDecimal.valueOf(reviews.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    /**
     * 만남 리뷰 평균 변화량만 등록자 매너온도에 반영합니다.
     * 단체 만남에서 리뷰 점수를 단순 누적하면 신청자 수가 많을수록 영향이 과해집니다.
     * 그래서 새 리뷰 작성 전 평균과 작성 후 평균의 차이만큼만 현재 매너온도에 더합니다.
     */
    private void updateAuthorMannerTemperatureByMeetingAverage(
            Long authorId,
            BigDecimal previousMeetingAverageScore,
            BigDecimal currentMeetingAverageScore
    ) {
        BigDecimal currentTemperature = userMannerService.getMannerTemperature(authorId);
        BigDecimal averageScoreDelta = currentMeetingAverageScore.subtract(previousMeetingAverageScore);

        BigDecimal changedTemperature = currentTemperature
                .add(averageScoreDelta.multiply(MANNER_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);

        userMannerService.updateMannerTemperature(authorId, clampMannerTemperature(changedTemperature));
    }


    /**
     * 매너 온도를 허용 범위 안으로 제한합니다.
     * 최소 0도, 최대 99도 범위를 벗어나지 않도록 보정합니다.
     * compareTo는 BigDecimal 비교용 메서드이다.
     * 이런식으로 이미 정의되어 있다.
     * a.compareTo(b) < 0  // a가 b보다 작다
     * a.compareTo(b) > 0  // a가 b보다 크다
     * a.compareTo(b) == 0 // 같다
     */
    private BigDecimal clampMannerTemperature(BigDecimal mannerTemperature) {
        if (mannerTemperature.compareTo(MIN_MANNER_TEMPERATURE) < 0) {
            return MIN_MANNER_TEMPERATURE;
        }

        if (mannerTemperature.compareTo(MAX_MANNER_TEMPERATURE) > 0) {
            return MAX_MANNER_TEMPERATURE;
        }

        return mannerTemperature;
    }

    /**
     * 후기 ID별 좋아요 태그 목록을 Map 형태로 변환합니다.
     * 받은 후기 목록 조회 시 각 후기마다 태그를 따로 조회하면 N+1 문제가 생길 수 있으므로,
     * reviewIds로 한 번에 조회한 뒤 reviewId 기준으로 그룹핑합니다.
     */
    private Map<Long, List<ReviewGoodTag>> getGoodTagMap(List<Review> reviews) {
        List<Long> reviewIds = extractReviewIds(reviews);

        if (reviewIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return reviewGoodTagRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        ReviewGoodTagEntity::getReviewId,
                        Collectors.mapping(ReviewGoodTagEntity::getTag, Collectors.toList())
                ));
    }

    /**
     * 후기 ID별 아쉬워요 태그 목록을 Map 형태로 변환합니다.
     * 받은 후기 목록 조회 시 각 후기마다 태그를 따로 조회하지 않기 위해,
     * reviewIds로 한 번에 조회한 뒤 reviewId 기준으로 그룹핑합니다.
     */
    private Map<Long, List<ReviewBadTag>> getBadTagMap(List<Review> reviews) {
        List<Long> reviewIds = extractReviewIds(reviews);

        if (reviewIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return reviewBadTagRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        ReviewBadTagEntity::getReviewId,
                        Collectors.mapping(ReviewBadTagEntity::getTag, Collectors.toList())
                ));
    }

    /**
     * 후기 목록에서 reviewId만 추출합니다.
     * 좋아요/아쉬워요 태그를 bulk 조회할 때 사용합니다.
     * 예를 들어, 이런식으로 가능.
     * 리뷰 1번: ON_TIME, KIND
     * 리뷰 2번: GOOD_COMMUNICATION
     * 리뷰 3번: WANT_MEET_AGAIN, CLEAN_MANNER
     */
    private List<Long> extractReviewIds(List<Review> reviews) {
        return reviews.stream()
                .map(Review::getId)
                .toList();
    }

    /**
     * 선택된 아쉬워요 태그 중 다시 만나고 싶지 않아요 태그가 있는지 확인합니다.
     */
    private boolean containsDoNotWantToMeetAgainTag(List<ReviewBadTag> badTags) {
        return badTags.contains(ReviewBadTag.DO_NOT_WANT_TO_MEET_AGAIN);
    }

    /**
     * 요청으로 들어온 태그 목록에서 중복 값을 제거합니다.
     * LinkedHashSet을 사용해 사용자가 선택한 순서는 유지하면서,
     * 같은 태그가 중복 저장되지 않도록 합니다.
     */
    private <T> List<T> distinct(List<T> values) {
        if (values == null) {
            return List.of();
        }

        return new LinkedHashSet<>(values).stream().toList();
    }
}
