package com.example.team3final.domain.review.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.common.exception.ReviewException;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.review.dto.request.CreateReviewRequestDto;
import com.example.team3final.domain.review.dto.response.CreateReviewResponseDto;
import com.example.team3final.domain.review.dto.response.GetReceivedReviewsResponseDto;
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
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 *
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
     *
     * 매칭 완료 시점으로부터 7일 이내에만 후기를 작성할 수 있습니다.
     */
    private static final int REVIEW_WRITE_DEADLINE_DAYS = 7;

    /**
     * 기본 매너 온도입니다.
     *
     * 후기가 없거나 평균 점수 변화량이 0이면 36.5도를 기준으로 유지합니다.
     */
    private static final BigDecimal BASE_MANNER_TEMPERATURE = new BigDecimal("36.5");


    /**
     * 태그 점수 변화량을 매너 온도에 반영하는 가중치입니다.
     *
     * 예: 평균 태그 점수 +2라면 36.5 + (2 * 0.5) = 37.5도
     */
    private static final BigDecimal MANNER_WEIGHT = new BigDecimal("0.5");

    /**
     * 매너 온도 하한선입니다.
     */
    private static final BigDecimal MIN_MANNER_TEMPERATURE = BigDecimal.ZERO;

    /**
     * 매너 온도 상한선입니다.
     */
    private static final BigDecimal MAX_MANNER_TEMPERATURE = new BigDecimal("99.0");

    private final ReviewRepository reviewRepository;
    private final ReviewGoodTagRepository reviewGoodTagRepository;
    private final ReviewBadTagRepository reviewBadTagRepository;
    private final MatchService matchService;
    private final PostService postService;
    private final UserService userService;
    private final UserPointService userPointService;


    /**
     * 후기를 생성합니다.
     *
     * 처리 흐름:
     * 1. 매칭/게시글 정보 조회
     * 2. 작성자가 매칭 당사자인지 검증
     * 3. 매칭 완료 상태와 작성 가능 기간 검증
     * 4. 중복 후기 작성 여부 검증
     * 5. 태그 점수 계산 후 Review 저장
     * 6. 선택 태그 상세 저장
     * 7. 후기 작성 보상 포인트 지급
     * 8. 후기를 받은 사용자의 매너 온도 재계산
     */
    @Override
    @Transactional
    public CreateReviewResponseDto createReview(
            Long matchId,
            Long writerId,
            CreateReviewRequestDto request
    ) {
        Match match = matchService.getMatchById(matchId);
        PostMatchInfoDto post = postService.getPostMatchInfo(match.getPostId());

        validateReviewCreatable(match, post.authorId(), writerId);

        List<ReviewGoodTag> goodTags = distinct(request.goodTags());
        List<ReviewBadTag> badTags = distinct(request.badTags());
        validateTags(goodTags, badTags);

        Review review = reviewRepository.save(
                Review.builder()
                        .matchId(match.getId())
                        .writerId(writerId)
                        .tagScoreDelta(calculateTagScoreDelta(goodTags, badTags))
                        .build()
        );

        saveGoodTags(review.getId(), goodTags);
        saveBadTags(review.getId(), badTags);

        userPointService.rewardReviewPoint(
                writerId,
                Review.REVIEW_REWARD_POINT,
                match.getId()
        );

        Long targetId = resolveTargetId(match, post.authorId(), writerId);
        updateTargetMannerTemperature(targetId);

        UserInfoDto targetInfo = userService.getUserInfo(targetId);

        return CreateReviewResponseDto.of(
                review,
                targetId,
                targetInfo.nickname(),
                goodTags,
                badTags,
                containsReportNeededTag(badTags)
        );
    }


    /**
     * 특정 사용자가 받은 후기 목록을 조회합니다.
     *
     * 본인 또는 같은 학교 사용자만 조회할 수 있으며,
     * Review에는 targetId를 저장하지 않기 때문에 matchId와 writerId를 기준으로
     * 받은 후기 목록을 계산합니다.
     */
    @Override
    public GetReceivedReviewsResponseDto getReceivedReviews(
            Long targetUserId,
            Long currentUserId,
            Pageable pageable
    ) {
        if (!targetUserId.equals(currentUserId)
                && !userService.isSameUniversity(targetUserId, currentUserId)) {
            throw new ReviewException(ErrorCode.REVIEW_ACCESS_DENIED);
        }

        Page<Review> reviews = reviewRepository.findReceivedReviews(targetUserId, pageable);
        Map<Long, List<ReviewGoodTag>> goodTagMap = getGoodTagMap(reviews.getContent());
        Map<Long, List<ReviewBadTag>> badTagMap = getBadTagMap(reviews.getContent());
        Map<Long, String> writerNicknameMap = userService.getUserNicknameMap(
                reviews.getContent().stream()
                        .map(Review::getWriterId)
                        .distinct()
                        .toList()
        );

        List<ReviewItemResponseDto> content = reviews.getContent().stream()
                .map(review -> {
                    List<ReviewGoodTag> goodTags = goodTagMap.getOrDefault(review.getId(), List.of());
                    List<ReviewBadTag> badTags = badTagMap.getOrDefault(review.getId(), List.of());

                    return ReviewItemResponseDto.of(
                            review,
                            writerNicknameMap.get(review.getWriterId()),
                            goodTags,
                            badTags,
                            containsReportNeededTag(badTags)
                    );
                })
                .toList();

        UserInfoDto targetInfo = userService.getUserInfo(targetUserId);

        return new GetReceivedReviewsResponseDto(
                targetUserId,
                targetInfo.nickname(),
                userService.getMannerTemperature(targetUserId),
                content,
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                reviews.hasNext()
        );
    }


    /**
     * 후기 작성 가능 조건을 검증합니다.
     *
     * 검증 항목:
     * - 작성자가 매칭 등록자 또는 신청자인지
     * - 매칭 상태가 COMPLETED인지
     * - 매칭 완료 후 7일 이내인지
     * - 같은 매칭에 이미 후기를 작성하지 않았는지
     */
    private void validateReviewCreatable(Match match, Long authorId, Long writerId) {
        if (!match.isParticipant(writerId, authorId)) {
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
     *
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
     *
     * 좋아요 태그는 각 +1점,
     * 아쉬워요 태그는 각 -1점으로 계산합니다.
     */
    private int calculateTagScoreDelta(List<ReviewGoodTag> goodTags, List<ReviewBadTag> badTags) {
        int goodScore = goodTags.stream()
                .mapToInt(ReviewGoodTag::getScoreDelta)
                .sum();
        int badScore = badTags.stream()
                .mapToInt(ReviewBadTag::getScoreDelta)
                .sum();

        return goodScore + badScore;
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
     *
     * REPORT_NEEDED 태그는 reportable=true로 저장되어
     * 이후 신고 흐름으로 연결할 수 있습니다.
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
     * 후기 작성자의 반대편 사용자를 계산합니다.
     *
     * Review에는 targetId를 저장하지 않으므로,
     * 게시글 작성자와 매칭 신청자 관계를 기준으로 받은 사람을 계산합니다.
     */
    private Long resolveTargetId(Match match, Long authorId, Long writerId) {
        if (authorId.equals(writerId)) {
            return match.getApplicantId();
        }

        return authorId;
    }


    /**
     * 후기를 받은 사용자의 매너 온도를 재계산합니다.
     *
     * 받은 후기들의 tagScoreDelta 평균을 구한 뒤,
     * 36.5 + (평균 점수 변화량 * 0.5) 공식으로 계산합니다.
     */
    private void updateTargetMannerTemperature(Long targetId) {
        List<Review> receivedReviews = reviewRepository.findReceivedReviews(targetId);

        double averageScoreDelta = receivedReviews.stream()
                .mapToInt(Review::getTagScoreDelta)
                .average()
                .orElse(0.0);

        BigDecimal mannerTemperature = BASE_MANNER_TEMPERATURE
                .add(BigDecimal.valueOf(averageScoreDelta).multiply(MANNER_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);

        userService.updateMannerTemperature(targetId, clampMannerTemperature(mannerTemperature));
    }


    /**
     * 매너 온도를 허용 범위 안으로 제한합니다.
     *
     * 최소 0도, 최대 99도 범위를 벗어나지 않도록 보정합니다.
     *
     * compareTo는 BigDecimal 비교용 메서드이다.
     *
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
     *
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
     *
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
     *
     * 좋아요/아쉬워요 태그를 bulk 조회할 때 사용합니다.
     *
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
     * 선택된 아쉬워요 태그 중 신고 연결 태그가 있는지 확인합니다.
     *
     * REPORT_NEEDED 태그가 포함되어 있으면 프론트에서 신고 작성 흐름을 열 수 있도록
     * reportNeeded=true로 응답합니다.
     */
    private boolean containsReportNeededTag(List<ReviewBadTag> badTags) {
        return badTags.stream().anyMatch(ReviewBadTag::isReportable);
    }

    /**
     * 요청으로 들어온 태그 목록에서 중복 값을 제거합니다.
     *
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
