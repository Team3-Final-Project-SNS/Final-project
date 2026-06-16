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
 * Review 도메인은 매칭/게시글/유저 Repository를 직접 참조하지 않고,
 * 각 도메인 Service를 통해 필요한 정보만 조회합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /** 후기 작성 가능 기간: 매칭 완료 시점으로부터 7일 이내 */
    private static final int REVIEW_WRITE_DEADLINE_DAYS = 7;

    /**
     * 태그 점수 → 매너온도 변환 가중치
     * 익명성 보장 및 역산 방지를 위해 0.1592로 설정
     * 예: averageScoreDelta = +2 → temperatureDelta = +2 × 0.1592 = +0.3도 (반올림)
     */
    private static final BigDecimal MANNER_WEIGHT = new BigDecimal("0.1592");

    /**
     * 한 만남 리뷰로 변동할 수 있는 온도 변동치의 상/하한
     * 단체 만남에서도 한 만남당 최대 ±1.6도 이상 변하지 않도록 제한
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
     * 5. save() 전 이전 만남 평균 점수 기록 (★ 반드시 save 전에 호출해야 함)
     * 6. 태그 점수 계산 후 Review 저장
     * 7. 선택 태그 상세 저장
     * 8. 후기 작성 보상 포인트 지급
     * 9. save() 후 현재 만남 평균 점수 계산 → 변화량 → 온도 변동치 계산
     * 10. 비관락으로 최종 온도 DB 반영
     * ★ 온도 계산 흐름 (예: 좋았어요 2개 선택 → tagScoreDelta = +2):
     *   previousMeetingAverageScore = 0.00  (save 전, 리뷰 없음)
     *   currentMeetingAverageScore  = 2.00  (save 후, 방금 저장한 리뷰 포함)
     *   averageScoreDelta = 2.00 - 0.00 = 2.00
     *   temperatureDelta  = 2.00 × 0.1592 = 0.3184 → setScale(1) → 0.3
     *   finalTemperature  = 36.5 + 0.3 = 36.8
     *   → updateMannerTemperatureWithLock(authorId, 0.3) 으로 비관락 적용해 DB에 36.8 저장
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

        // 이 게시글에 연결된 모든 matchId 목록 조회 (1:N 단체 만남 대비)
        List<Long> postMatchIds = matchInternalService.getMatchIdsByPostId(post.postId());

        // ★ 반드시 save() 전에 이전 평균을 기록해야 함
        // save() 이후에 호출하면 방금 저장한 리뷰가 포함되어 "이전 평균"이 오염됨
        BigDecimal previousMeetingAverageScore = calculateMeetingAverageScore(postMatchIds);

        // 태그 점수 계산 (-3 ~ +2 범위로 클램핑됨)
        int tagScoreDelta = calculateTagScoreDelta(goodTags, badTags);

        // 2차 방어: DB UNIQUE 제약 (match_id, writer_id)
        // 동시 요청이 1차 체크를 둘 다 통과해도 하나만 저장됨
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
            throw new ReviewException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        saveGoodTags(review.getId(), goodTags);
        saveBadTags(review.getId(), badTags);

        // 후기 작성 보상 포인트 지급
        userPointService.rewardReviewPoint(writerId, Review.REVIEW_REWARD_POINT, match.getId());

        // 후기 작성 포인트 지급 알림
        notificationPublisher.sendReviewPoint(review.getWriterId(), review.getId());

        // 다시 만나고 싶지 않아요 선택 시 양방향 블라인드 처리
        if (containsDoNotWantToMeetAgainTag(badTags)) {
            reviewAvoidanceService.createAvoidRelation(writerId, authorId, review.getId());
        }

        // 매너온도 갱신
        // save() 이후 현재 평균을 구하면 방금 저장된 리뷰가 포함되어 올바르게 계산됨
        // previousMeetingAverageScore는 save() 전에 캡처했으므로 오염 없음
        BigDecimal currentMeetingAverageScore = calculateMeetingAverageScore(postMatchIds);

        // 평균 변화량 (이전 평균 → 현재 평균의 차이)
        // 1:N 단체 만남에서 리뷰가 누적될수록 변화량이 줄어들어 온도 과잉 반영을 방지
        BigDecimal averageScoreDelta = currentMeetingAverageScore.subtract(previousMeetingAverageScore);

        // 변화량 × 가중치 = 온도 변동치 (소수점 첫째 자리 반올림)
        BigDecimal temperatureDelta = averageScoreDelta
                .multiply(MANNER_WEIGHT)
                .setScale(1, RoundingMode.HALF_UP);

        // 한 만남으로 변동할 수 있는 온도 변동치를 ±1.6도로 제한
        if (temperatureDelta.compareTo(MAX_TEMPERATURE_DELTA_LIMIT) > 0) {
            temperatureDelta = MAX_TEMPERATURE_DELTA_LIMIT;
        } else if (temperatureDelta.compareTo(MIN_TEMPERATURE_DELTA_LIMIT) < 0) {
            temperatureDelta = MIN_TEMPERATURE_DELTA_LIMIT;
        }

        // 비관락으로 온도 변동치만 넘김
        // UserMannerServiceImpl 내부에서 비관락으로 currentTemperature를 읽어
        // currentTemperature + temperatureDelta 를 계산하고 0~99 클램핑 후 저장
        // → 이중 계산 없이 정확한 결과 보장
        userMannerService.updateMannerTemperatureWithLock(authorId, temperatureDelta);

        // 매너 온도 변경 알림
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
     * 본인이 작성한 후기만 확인할 수 있습니다.
     */
    @Override
    public GetWrittenReviewsResponseDto getWrittenReviews(Long currentUserId) {
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

        return new GetWrittenReviewsResponseDto(currentUserId, currentUserInfo.nickname(), content);
    }

    @Override
    public List<Long> getAvoidedUserIds(Long userId) {
        return reviewAvoidanceService.getAvoidedUserIds(userId);
    }

    @Override
    public boolean existsAvoidRelation(Long userId, Long otherUserId) {
        return reviewAvoidanceService.existsAvoidRelation(userId, otherUserId);
    }


    // ===== private 검증/계산 메서드 =====

    /**
     * 후기 작성 가능 조건을 검증합니다.
     */
    private void validateReviewCreatable(Match match, PostMatchInfoDto post, Long writerId) {
        if (!match.isParticipant(writerId, post.authorId())) {
            throw new MatchException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 등록자는 후기를 작성할 수 없음
        if (post.authorId().equals(writerId)) {
            throw new ReviewException(ErrorCode.REVIEW_AUTHOR_NOT_ALLOWED);
        }

        // writerId는 반드시 현재 match의 applicantId여야 함
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
     * 좋았어요/아쉬웠어요는 동시에 선택할 수 없고, 둘 중 하나는 반드시 선택해야 합니다.
     */
    private void validateTags(List<ReviewGoodTag> goodTags, List<ReviewBadTag> badTags) {
        boolean goodTagSelected = !goodTags.isEmpty();
        boolean badTagSelected = !badTags.isEmpty();

        // 둘 다 선택 or 둘 다 미선택이면 예외
        if (goodTagSelected == badTagSelected) {
            throw new ReviewException(ErrorCode.REVIEW_INVALID_TAG);
        }
    }

    /**
     * 선택된 태그를 기반으로 점수 변화량을 계산합니다.
     * 결과 범위: -3 ~ +2 (익명성 보장을 위한 클램핑)
     */
    private int calculateTagScoreDelta(List<ReviewGoodTag> goodTags, List<ReviewBadTag> badTags) {
        int goodScore = goodTags.stream().mapToInt(ReviewGoodTag::getScoreDelta).sum();
        int badScore = badTags.stream().mapToInt(ReviewBadTag::getScoreDelta).sum();
        int totalRawScore = goodScore + badScore;

        int maxLimit = 2;
        int minLimit = -3;
        return Math.max(minLimit, Math.min(maxLimit, totalRawScore));
    }

    /**
     * 한 만남에 작성된 리뷰들의 평균 점수를 계산합니다.
     * ★ 호출 시점에 따라 결과가 달라지므로 반드시 save() 전후로 각각 1번씩 호출해야 함
     *   save() 전  → previousMeetingAverageScore (이전 평균)
     *   save() 후  → currentMeetingAverageScore  (현재 평균, 방금 저장한 리뷰 포함)
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

        return totalScore.divide(BigDecimal.valueOf(reviews.size()), 2, RoundingMode.HALF_UP);
    }

    /** 좋아요 태그 선택 내역을 저장합니다. */
    private void saveGoodTags(Long reviewId, List<ReviewGoodTag> goodTags) {
        List<ReviewGoodTagEntity> entities = goodTags.stream()
                .map(tag -> ReviewGoodTagEntity.builder().reviewId(reviewId).tag(tag).build())
                .toList();
        reviewGoodTagRepository.saveAll(entities);
    }

    /** 아쉬워요 태그 선택 내역을 저장합니다. */
    private void saveBadTags(Long reviewId, List<ReviewBadTag> badTags) {
        List<ReviewBadTagEntity> entities = badTags.stream()
                .map(tag -> ReviewBadTagEntity.builder().reviewId(reviewId).tag(tag).build())
                .toList();
        reviewBadTagRepository.saveAll(entities);
    }

    /**
     * 후기 ID별 좋아요 태그 목록을 Map 형태로 변환합니다. (N+1 방지)
     */
    private Map<Long, List<ReviewGoodTag>> getGoodTagMap(List<Review> reviews) {
        List<Long> reviewIds = extractReviewIds(reviews);
        if (reviewIds.isEmpty()) return Collections.emptyMap();

        return reviewGoodTagRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        ReviewGoodTagEntity::getReviewId,
                        Collectors.mapping(ReviewGoodTagEntity::getTag, Collectors.toList())
                ));
    }

    /**
     * 후기 ID별 아쉬워요 태그 목록을 Map 형태로 변환합니다. (N+1 방지)
     */
    private Map<Long, List<ReviewBadTag>> getBadTagMap(List<Review> reviews) {
        List<Long> reviewIds = extractReviewIds(reviews);
        if (reviewIds.isEmpty()) return Collections.emptyMap();

        return reviewBadTagRepository.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        ReviewBadTagEntity::getReviewId,
                        Collectors.mapping(ReviewBadTagEntity::getTag, Collectors.toList())
                ));
    }

    /** 후기 목록에서 reviewId만 추출합니다. */
    private List<Long> extractReviewIds(List<Review> reviews) {
        return reviews.stream().map(Review::getId).toList();
    }

    /** 선택된 아쉬워요 태그 중 다시 만나고 싶지 않아요 태그가 있는지 확인합니다. */
    private boolean containsDoNotWantToMeetAgainTag(List<ReviewBadTag> badTags) {
        return badTags.contains(ReviewBadTag.DO_NOT_WANT_TO_MEET_AGAIN);
    }

    /**
     * 요청으로 들어온 태그 목록에서 중복 값을 제거합니다.
     * LinkedHashSet을 사용해 선택 순서는 유지하면서 중복 저장을 방지합니다.
     */
    private <T> List<T> distinct(List<T> values) {
        if (values == null) return List.of();
        return new LinkedHashSet<>(values).stream().toList();
    }
}