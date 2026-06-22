package com.example.team3final.domain.ai.matching.tool;


import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.ai.matching.dto.PostVectorSearchResultDto;
import com.example.team3final.domain.ai.matching.repository.PostVectorRepository;
import com.example.team3final.domain.ai.matching.util.AiMatchingMenuEvidence;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 매칭 AI에서 사용하는 백엔드 Tool입니다.
 *
 * 같은 학교의 모집 중인 식사팟 후보를 조회하고,
 * 로그인 사용자의 신청 가능 여부와 책임비 포인트 충족 여부를 검증합니다.
 *
 * 추천 후보 조회는 PostgreSQL pgvector의 매칭 게시글 벡터 인덱스를 의미 검색용 보조 인덱스로 먼저 사용합니다.
 * pgvector에는 장소명(placeName), 한마디(content), 시간대 표현의 embedding과 후보 필터링용 메타데이터를 저장하고,
 * 신청 가능 여부와 실시간 정합성은 기존 MySQL posts/matches/users 데이터를 기준으로 최종 검증합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiMatchingTool {

    private static final int MAX_RECOMMENDATION_CANDIDATES = 3;
    private static final int MAX_TOOL_CANDIDATES = 10;
    private static final int SEMANTIC_VECTOR_TOP_K = 20;
    private static final int MAX_MEAL_PARTY_SIZE = 5;

    private final UserInternalService userInternalService;
    private final PostInternalService postInternalService;
    private final MatchInternalService matchInternalService;
    private final AiProperties aiProperties;
    private final ObjectProvider<PostVectorRepository> postVectorRepositoryProvider;


    /**
     * 서비스 내부에서 사용하는 모집글 후보 조회 메서드입니다.
     *
     * 로그인 사용자의 학교 기준으로 같은 학교의 ACTIVE 사용자들이 작성한
     * OPEN 상태의 게시글을 조회하고, 본인 게시글 제외, 이미 신청한 글 여부,
     * 모집 마감 여부, 책임비 포인트 충족 여부를 검증합니다.
     *
     * @param userId 로그인 사용자 ID
     * @param universityId 로그인 사용자의 학교 ID
     * @param userPoint 로그인 사용자의 보유 포인트
     * @param condition 사용자의 자연어 식사 조건
     * @return 매칭 AI 프롬프트에 전달할 모집글 후보 목록
     */
    public List<AiMatchingPostToolResult> searchRecruitingMealPosts(
            Long userId,
            Long universityId,
            int userPoint,
            String condition
    ) {
        // User 도메인의 ACTIVE 사용자 조회 규칙을 재사용합니다.
        // AI 매칭 도메인은 UserRepository를 직접 참조하지 않고 추천 후보 작성자 ID만 전달받습니다.
        List<Long> sameUniversityUserIds =
                userInternalService.getActiveUserIdsByUniversityId(universityId);

        if (sameUniversityUserIds.isEmpty()) {
            return List.of();
        }

        SearchCondition searchCondition = SearchCondition.from(condition);

        List<Post> vectorCandidates = findCandidatePostsByVector(
                condition,
                userId,
                universityId,
                userPoint,
                sameUniversityUserIds,
                searchCondition
        );

        List<Post> filteredPosts = filterCandidatePosts(vectorCandidates, userId, searchCondition, false, false);
        if (filteredPosts.isEmpty() && searchCondition.requiresSemanticMenuMatch()) {
            // 치킨, 짜장처럼 게시글 한마디에 직접 들어간 메뉴 조건은 pgvector 후보가 비어도
            // MySQL 모집글 후보에서 텍스트 단서로 한 번 더 복구합니다.
            // 벡터 인덱스 누락이나 임계값 튜닝 문제로 명확한 메뉴 글이 빠지는 것을 막기 위한 보완 경로입니다.
            filteredPosts = filterCandidatePosts(
                    postInternalService.findAiMatchingCandidatePosts(
                            sameUniversityUserIds,
                            searchCondition.sort()
                    ),
                    userId,
                    searchCondition,
                    true,
                    true
            ).stream()
                    .filter(searchCondition::matchesMenuEvidence)
                    .toList();
        }

        if (filteredPosts.isEmpty() && !searchCondition.requiresSemanticVectorMatch()) {
            // 메뉴/분위기 같은 의미 조건이 없는 요청은 pgvector가 비어도 기존 모집글 조회로 복구할 수 있습니다.
            // 의미 조건이 있는 요청은 무관한 후보를 추천하지 않도록 벡터 결과가 없으면 비워둡니다.
            filteredPosts = filterCandidatePosts(
                    postInternalService.findAiMatchingCandidatePosts(
                            sameUniversityUserIds,
                            searchCondition.sort()
                    ),
                    userId,
                    searchCondition,
                    true,
                    true
            );
        }

        int candidateLimit = searchCondition.requiresSemanticVectorMatch()
                ? MAX_TOOL_CANDIDATES
                : MAX_RECOMMENDATION_CANDIDATES;

        return filteredPosts.stream()
                .limit(candidateLimit)
                .map(post -> {
                    boolean alreadyApplied =
                            matchInternalService.hasAppliedToPost(post.getId(), userId);

                    boolean pointAffordable = userPoint >= post.getAuthorDeposit();

                    boolean applicationAvailable =
                            post.isOpen()
                                    && !post.isFull()
                                    && !alreadyApplied
                                    && pointAffordable;

                    String unavailableReason = resolveUnavailableReason(
                            post,
                            alreadyApplied,
                            pointAffordable
                    );

                    return new AiMatchingPostToolResult(
                            post.getId(),
                            post.getPlaceName(),
                            post.getMeetAt().toString(),
                            post.getAuthorDeposit(),
                            post.getContent(),
                            post.getCurrentApplicants(),
                            post.getMaxApplicants(),
                            Math.max(0, post.getMaxApplicants() - post.getCurrentApplicants()),
                            applicationAvailable,
                            pointAffordable,
                            unavailableReason
                    );
                })
                .toList();
    }

    private List<Post> filterCandidatePosts(
            List<Post> posts,
            Long userId,
            SearchCondition searchCondition,
            boolean applyConditionSort,
            boolean excludeAuthor
    ) {
        var stream = posts.stream()
                .filter(post -> !excludeAuthor || !post.isAuthor(userId))
                .filter(searchCondition::matchesTime)
                .filter(searchCondition::matchesPartySize);

        if (applyConditionSort) {
            stream = stream.sorted(searchCondition.comparator());
        }

        return stream.toList();
    }

    private List<Post> findCandidatePostsByVector(
            String condition,
            Long userId,
            Long universityId,
            int userPoint,
            List<Long> sameUniversityUserIds,
            SearchCondition searchCondition
    ) {
        PostVectorRepository postVectorRepository = postVectorRepositoryProvider == null
                ? null
                : postVectorRepositoryProvider.getIfAvailable();
        if (postVectorRepository == null) {
            return List.of();
        }

        try {
            int minimumTopK = searchCondition.requiresSemanticVectorMatch()
                    ? SEMANTIC_VECTOR_TOP_K
                    : MAX_RECOMMENDATION_CANDIDATES;
            int topK = Math.max(aiProperties.getMatching().getRag().getTopK(), minimumTopK);
            double threshold = resolveVectorSimilarityThreshold(searchCondition);
            List<PostVectorSearchResultDto> searchResults = postVectorRepository.search(
                            condition,
                            universityId,
                            userId,
                            userPoint,
                            topK,
                            threshold
                    );
            if (log.isInfoEnabled()) {
                log.info("[AiMatchingTool] 벡터 후보 - condition: {}, threshold: {}, results: {}",
                        condition,
                        threshold,
                        searchResults.stream()
                                .map(result -> "%d(%.3f)".formatted(result.postId(), result.similarity()))
                                .toList());
            }

            List<Long> postIds = searchResults
                    .stream()
                    .map(PostVectorSearchResultDto::postId)
                    .toList();

            // pgvector는 의미 검색과 메타데이터 필터로 postId 후보를 제공하는 보조 인덱스입니다.
            // 최종 추천 대상은 MySQL에서 같은 학교 작성자, OPEN 상태, 미래 약속 시간 조건을 다시 검증합니다.
            return postInternalService.findAiMatchingCandidatePostsByIds(postIds, sameUniversityUserIds);
        } catch (Exception e) {
            log.warn("[AiMatchingTool] 게시글 벡터 검색 실패. MySQL 후보 조회로 대체합니다.", e);
            return List.of();
        }
    }

    private double resolveVectorSimilarityThreshold(SearchCondition searchCondition) {
        double configuredThreshold = aiProperties.getMatching().getRag().getSimilarityThreshold();
        if (searchCondition.requiresSemanticMenuMatch()) {
            double menuThreshold = aiProperties.getMatching().getRag().getMenuSimilarityThreshold();
            return Math.min(configuredThreshold, menuThreshold);
        }

        if (searchCondition.requiresSemanticAtmosphereMatch()) {
            double atmosphereThreshold = aiProperties.getMatching().getRag().getAtmosphereSimilarityThreshold();
            return Math.min(configuredThreshold, atmosphereThreshold);
        }

        return configuredThreshold;
    }

    /**
     * LLM Tool Calling에서 사용할 모집글 후보 조회 메서드입니다.
     *
     * 사용자의 이메일과 자연어 조건을 기반으로 신청 가능한 식사팟 후보를 조회합니다.
     * 현재 매칭 서비스에서는 AiMatchingSessionTool을 통해 로그인 사용자의 email을 고정한 뒤,
     * LLM이 이 조회 로직을 Tool Calling으로 호출합니다.
     */
    @Tool(
            description = "사용자의 자연어 조건에 맞는 모집 중인 식사팟을 조회합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public List<AiMatchingPostToolResult> searchRecruitingMealPostsForAi(
            @ToolParam(description = "사용자의 이메일", required = true)
            String email,

            @ToolParam(description = "사용자의 식사 조건. 예: 오늘 저녁 조용하게 밥 먹을 사람", required = true)
            String condition
    ) {
        User user = userInternalService.findByEmail(email);

        return searchRecruitingMealPosts(
                user.getId(),
                user.getUniversityId(),
                user.getTotalPoint(),
                condition
        );
    }

    /**
     * 특정 게시글에 대해 사용자가 신청 가능한지 검증하는 Tool입니다.
     *
     * 게시글 상태, 본인 게시글 여부, 이미 신청한 여부,
     * 모집 인원 마감 여부, 책임비 포인트 충족 여부를 확인합니다.
     *
     * 추후 LLM이 특정 게시글을 추천한 뒤 신청 가능 여부를 재검증하는
     * Tool Calling 흐름에서 사용할 수 있습니다.
     */
    @Tool(
            description = "특정 게시글에 사용자가 신청 가능한지 확인합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public AiMatchingPostToolResult checkApplicationAvailability(
            @ToolParam(description = "사용자의 이메일", required = true)
            String email,

            @ToolParam(description = "게시글 ID", required = true)
            Long postId
    ) {
        User user = userInternalService.findByEmail(email);

        Post post = postInternalService.getPostById(postId);

        boolean alreadyApplied =
                matchInternalService.hasAppliedToPost(post.getId(), user.getId());

        boolean pointAffordable = user.getTotalPoint() >= post.getAuthorDeposit();

        boolean applicationAvailable =
                post.isOpen()
                        && !post.isFull()
                        && !post.isAuthor(user.getId())
                        && !alreadyApplied
                        && pointAffordable;

        String unavailableReason = resolveUnavailableReason(
                post,
                alreadyApplied,
                pointAffordable
        );

        if (post.isAuthor(user.getId())) {
            unavailableReason = "본인 게시글에는 신청할 수 없습니다.";
        }

        return new AiMatchingPostToolResult(
                post.getId(),
                post.getPlaceName(),
                post.getMeetAt().toString(),
                post.getAuthorDeposit(),
                post.getContent(),
                post.getCurrentApplicants(),
                post.getMaxApplicants(),
                Math.max(0, post.getMaxApplicants() - post.getCurrentApplicants()),
                applicationAvailable,
                pointAffordable,
                unavailableReason
        );
    }

    /**
     * 신청 불가 사유를 사용자에게 안내할 수 있는 문장으로 변환합니다.
     */
    private String resolveUnavailableReason(
            Post post,
            boolean alreadyApplied,
            boolean pointAffordable
    ) {
        if (!post.isOpen()) {
            return "모집 중인 게시글이 아닙니다.";
        }

        if (post.isFull()) {
            return "모집 인원이 마감되었습니다.";
        }

        if (alreadyApplied) {
            return "이미 신청한 게시글입니다.";
        }

        if (!pointAffordable) {
            return "보유 포인트가 책임비보다 부족합니다.";
        }

        return null;
    }



    /**
     * AI 매칭 후보 조회에 사용하는 검색 조건입니다.
     *
     * 사용자의 자연어 요청을 간단한 규칙으로 해석하여
     * 게시글 후보 조회 이후의 필터링과 정렬에 사용합니다.
     *
     * 현재 처리하는 조건은 다음과 같습니다.
     * - 책임비 정렬 조건
     * - 날짜 및 시간대 조건
     *
     * 음식/메뉴/분위기처럼 의미 해석이 필요한 조건은 서버에서 키워드로 자르지 않고,
     * pgvector 검색과 LLM 판단에 맡깁니다.
     */
    private record SearchCondition(
            Sort sort,
            Comparator<Post> comparator,
            boolean semanticMenuMatch,
            boolean semanticAtmosphereMatch,
            List<String> menuEvidenceTokens,
            TimeRange timeRange,
            PartySizeCondition partySizeCondition
    ) {

        private static SearchCondition from(String condition) {
            String normalized = normalize(condition);
            Sort sort = resolveSort(normalized);
            Comparator<Post> comparator = resolveComparator(normalized);
            boolean semanticMenuMatch = AiMatchingMenuEvidence.hasMenuIntent(condition);
            boolean semanticAtmosphereMatch = hasAtmosphereCondition(normalized);
            List<String> menuEvidenceTokens = AiMatchingMenuEvidence.extractTokens(condition);
            TimeRange timeRange = resolveTimeRange(normalized);
            PartySizeCondition partySizeCondition = resolvePartySizeCondition(normalized);

            return new SearchCondition(
                    sort,
                    comparator,
                    semanticMenuMatch,
                    semanticAtmosphereMatch,
                    menuEvidenceTokens,
                    timeRange,
                    partySizeCondition
            );
        }

        private record TimeRange(
                LocalDateTime startAt,
                LocalDateTime endAt
        ) {

            // 게시글 시간이 검색 시간 범위 안에 있는지 확인
            private boolean contains(LocalDateTime target) {
                return !target.isBefore(startAt) && !target.isAfter(endAt);
            }
        }

        private boolean requiresSemanticMenuMatch() {
            return semanticMenuMatch;
        }

        private boolean requiresSemanticVectorMatch() {
            return semanticMenuMatch || semanticAtmosphereMatch;
        }

        private boolean requiresSemanticAtmosphereMatch() {
            return semanticAtmosphereMatch;
        }

        private boolean matchesMenuEvidence(Post post) {
            if (menuEvidenceTokens == null || menuEvidenceTokens.isEmpty()) {
                return false;
            }

            return AiMatchingMenuEvidence.hasEvidence(
                    post.getPlaceName() + " " + post.getContent(),
                    menuEvidenceTokens
            );
        }

        // 시간대 조건과 게시글 약속 시간이 맞는지 확인
        private boolean matchesTime(Post post) {
            if (timeRange == null) {
                return true;
            }

            return timeRange.contains(post.getMeetAt());
        }

        // 사용자가 원하는 식사팟 인원이 있으면 등록자 포함 최대 정원 기준으로 후보를 거릅니다.
        private boolean matchesPartySize(Post post) {
            if (partySizeCondition == null) {
                return true;
            }

            return partySizeCondition.matches(post);
        }

        // 자연어 책임비 조건을 DB 정렬 조건으로 변환
        private static Sort resolveSort(String normalized) {
            if (isHighDepositCondition(normalized)) {
                return Sort.by(Sort.Direction.DESC, "authorDeposit")
                        .and(Sort.by(Sort.Direction.ASC, "meetAt"));
            }

            if (isLowDepositCondition(normalized)) {
                return Sort.by(Sort.Direction.ASC, "authorDeposit")
                        .and(Sort.by(Sort.Direction.ASC, "meetAt"));
            }

            return Sort.by(Sort.Direction.ASC, "meetAt");
        }

        // 조회 후 최종 후보 정렬 기준 생성
        private static Comparator<Post> resolveComparator(String normalized) {
            Comparator<Post> meetAtAsc = Comparator.comparing(Post::getMeetAt);

            if (isHighDepositCondition(normalized)) {
                return Comparator.comparingInt(Post::getAuthorDeposit).reversed()
                        .thenComparing(meetAtAsc);
            }

            if (isLowDepositCondition(normalized)) {
                return Comparator.comparingInt(Post::getAuthorDeposit)
                        .thenComparing(meetAtAsc);
            }

            return meetAtAsc;
        }

        // 조용함, 활발함, 수다, 편한 분위기처럼 DB 컬럼으로 직접 필터링하기 어려운 조건은
        // 게시글 한마디(content) 의미 검색에 의존하므로 별도 벡터 임계값을 사용합니다.
        private static boolean hasAtmosphereCondition(String normalized) {
            return containsAny(
                    normalized,
                    "조용",
                    "차분",
                    "활발",
                    "재밌",
                    "재미",
                    "수다",
                    "대화",
                    "편하",
                    "가볍",
                    "친근",
                    "친목",
                    "어색하지",
                    "말많",
                    "말많은"
            );
        }

        // 자연어 날짜/시간 표현을 시간 범위로 변환
        private static TimeRange resolveTimeRange(String normalized) {
            TimeRange hourRange = resolveHourRange(normalized);

            if (hourRange != null) {
                return hourRange;
            }

            LocalDate today = LocalDate.now();

            if (containsAny(normalized, "오늘")) {
                return new TimeRange(
                        today.atStartOfDay(),
                        today.atTime(23, 59, 59)
                );
            }

            if (containsAny(normalized, "내일")) {
                LocalDate tomorrow = today.plusDays(1);

                return new TimeRange(
                        tomorrow.atStartOfDay(),
                        tomorrow.atTime(23, 59, 59)
                );
            }

            if (containsAny(normalized, "아침", "조식", "모닝")) {
                return new TimeRange(
                        today.atTime(7, 0),
                        today.atTime(10, 0)
                );
            }

            if (containsAny(normalized, "브런치")) {
                return new TimeRange(
                        today.atTime(10, 0),
                        today.atTime(12, 0)
                );
            }

            if (containsAny(normalized, "점심", "런치")) {
                return new TimeRange(
                        today.atTime(11, 0),
                        today.atTime(14, 0)
                );
            }

            if (containsAny(normalized, "저녁", "퇴근후")) {
                return new TimeRange(
                        today.atTime(17, 0),
                        today.atTime(21, 0)
                );
            }

            if (containsAny(normalized, "밤", "야식")) {
                return new TimeRange(
                        today.atTime(21, 0),
                        today.plusDays(1).atTime(1, 0)
                );
            }

            return null;
        }


        // "3시", "오후 6시" 같은 특정 시간 표현 처리
        private static TimeRange resolveHourRange(String normalized) {
            Pattern pattern = Pattern.compile("(\\d{1,2})시");
            Matcher matcher = pattern.matcher(normalized);

            if (!matcher.find()) {
                return null;
            }

            // matcher.group(1)은 괄호 안에 있는 숫자 부분만 가져온다.
            int hour = Integer.parseInt(matcher.group(1));

            boolean hasAm = containsAny(normalized, "오전");
            boolean hasPm = containsAny(normalized, "오후");

            if (hasPm && hour >= 1 && hour <= 11) {
                hour += 12;
            }

            if (hasAm && hour == 12) {
                hour = 0;
            }

            if (hour < 0 || hour > 23) {
                return null;
            }

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime center = today.atTime(hour, 0);

           // 오전/오후 표현이 없고 1~11시라면, 다가오는 오후 시간을 우선 해석
            if (!hasAm && !hasPm && hour >= 1 && hour <= 11) {
                LocalDateTime pmCenter = today.atTime(hour + 12, 0);

                if (!pmCenter.plusHours(1).isBefore(now)) {
                    center = pmCenter;
                }
            }

        // 이미 지난 시간이면 다음 날 같은 시간으로 해석
            if (center.plusHours(1).isBefore(now)) {
                center = center.plusDays(1);
            }

            return new TimeRange(
                    center.minusHours(1),
                    center.plusHours(1)
            );
        }

        private enum PartySizeMode {
            EXACT_CAPACITY,
            MIN_CAPACITY
        }

        private record PartySizeCondition(
                int partySize,
                PartySizeMode mode
        ) {

            private boolean matches(Post post) {
                if (partySize > MAX_MEAL_PARTY_SIZE) {
                    return false;
                }

                if (mode == PartySizeMode.MIN_CAPACITY) {
                    return post.getMaxApplicants() >= partySize;
                }

                return post.getMaxApplicants() == partySize;
            }
        }

        private record PartySizeToken(
                int value,
                int start,
                int end
        ) {
        }

        // 숫자 또는 한글 수사 + 인원 단위 구조를 먼저 찾고, 주변 의미로 정원 조건을 결정합니다.
        private static PartySizeCondition resolvePartySizeCondition(String normalized) {
            PartySizeToken token = findPartySizeToken(normalized);
            if (token == null || token.value() <= 0) {
                return null;
            }

            PartySizeMode mode = hasCapacityAvailabilityIntent(normalized, token)
                    ? PartySizeMode.MIN_CAPACITY
                    : PartySizeMode.EXACT_CAPACITY;

            return new PartySizeCondition(token.value(), mode);
        }

        private static PartySizeToken findPartySizeToken(String normalized) {
            Matcher digitMatcher = Pattern.compile("(\\d+)(?:명|인)").matcher(normalized);
            if (digitMatcher.find()) {
                return new PartySizeToken(
                        Integer.parseInt(digitMatcher.group(1)),
                        digitMatcher.start(),
                        digitMatcher.end()
                );
            }

            Matcher koreanMatcher = Pattern.compile("([가-힣]+?)(?:명|인)").matcher(normalized);
            while (koreanMatcher.find()) {
                Integer value = parseKoreanPartySize(koreanMatcher.group(1));
                if (value != null) {
                    return new PartySizeToken(value, koreanMatcher.start(), koreanMatcher.end());
                }
            }

            return null;
        }

        private static Integer parseKoreanPartySize(String text) {
            return switch (text) {
                case "한", "하나", "일" -> 1;
                case "두", "둘", "이" -> 2;
                case "세", "셋", "삼" -> 3;
                case "네", "넷", "사" -> 4;
                case "다섯", "오" -> 5;
                default -> null;
            };
        }

        private static boolean hasCapacityAvailabilityIntent(String normalized, PartySizeToken token) {
            int windowStart = Math.max(0, token.start() - 8);
            int windowEnd = Math.min(normalized.length(), token.end() + 10);
            String nearText = normalized.substring(windowStart, windowEnd);

            return containsAny(nearText, "가능", "까지", "이상", "수용", "자리", "여유", "남");
        }


        // 책임비 높은 순 요청 여부
        // 앞에서 시간 순으로 가져오기때문에 한번 정렬해서 준다.
        // Tool 단계에서 먼저 후보군을 책임비 높은 순으로 정리해서 LLM에 넘긴다.
        private static boolean isHighDepositCondition(String normalized) {
            return containsAny(
                    normalized,
                    "책임비가장높",
                    "책임비가가장높",
                    "책임비제일높",
                    "책임비가제일높",
                    "가장높",
                    "제일높",
                    "높은순",
                    "비싼"
            );
        }

        // 책임비 낮은 순 요청 여부
        // 앞에서 시간 순으로 가져오기때문에 한번 정렬해서 준다.
        // Tool 단계에서 먼저 후보군을 책임비 낮은 순으로 정리해서 LLM에 넘긴다.
        private static boolean isLowDepositCondition(String normalized) {
            return containsAny(
                    normalized,
                    "책임비낮",
                    "책임비가낮",
                    "책임비가장낮",
                    "책임비가가장낮",
                    "책임비제일낮",
                    "낮은순",
                    "가장낮",
                    "제일낮",
                    "저렴",
                    "싼"
            );
        }

        // 문자열에 키워드 중 하나라도 포함되는지 확인
        // 여러 검색 키워드를 편하게 넘기기 위해 String... 가변 인자를 사용한 것.
        // 여러 키워드를 바로 넘길 수 있다.
        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }

            return false;
        }

        // 검색 비교를 위한 공백 제거 + 소문자 변환
        private static String normalize(String text) {
            if (text == null) {
                return "";
            }

            return text.replace(" ", "").toLowerCase();
        }

    }



}
