package com.example.team3final.domain.ai.matching.tool;


import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.ai.matching.dto.PostVectorSearchResultDto;
import com.example.team3final.domain.ai.matching.repository.PostVectorRepository;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

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
    private static final int MAX_MEAL_PARTY_SIZE = 5;

    private final UserService userService;
    private final PostService postService;
    private final MatchService matchService;
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
                userService.getActiveUserIdsByUniversityId(universityId);

        if (sameUniversityUserIds.isEmpty()) {
            return List.of();
        }

        SearchCondition searchCondition = SearchCondition.from(condition);

        List<Post> vectorCandidates = findCandidatePostsByVector(
                condition,
                userId,
                universityId,
                userPoint,
                sameUniversityUserIds
        );
        List<Post> posts = vectorCandidates;
        if (posts.isEmpty()) {
            // pgvector가 비활성화되었거나 후보를 찾지 못하면 기존 MySQL 조회로 추천 기능을 계속 제공합니다.
            // 이 fallback 덕분에 벡터 DB 장애가 곧바로 매칭 AI 전체 장애로 이어지지 않습니다.
            posts = postService.findAiMatchingCandidatePosts(
                    sameUniversityUserIds,
                    searchCondition.sort()
            );
        }

        List<Post> filteredPosts = filterCandidatePosts(posts, userId, searchCondition);
        if (filteredPosts.isEmpty() && !vectorCandidates.isEmpty()) {
            // 벡터 검색 후보가 최종 메뉴/시간 필터에서 모두 제외되면 MySQL 모집글 후보로 한 번 더 넓혀봅니다.
            // pgvector는 의미 검색 보조 인덱스이므로, topK 안에 문자 조건과 정확히 맞는 게시글이 없을 수 있습니다.
            filteredPosts = filterCandidatePosts(
                    postService.findAiMatchingCandidatePosts(
                            sameUniversityUserIds,
                            searchCondition.sort()
                    ),
                    userId,
                    searchCondition
            );
        }

        return filteredPosts.stream()
                .limit(MAX_RECOMMENDATION_CANDIDATES)
                .map(post -> {
                    boolean alreadyApplied =
                            matchService.hasAppliedToPost(post.getId(), userId);

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

    private List<Post> filterCandidatePosts(List<Post> posts, Long userId, SearchCondition searchCondition) {
        return posts.stream()
                .filter(post -> !post.isAuthor(userId))
                .filter(searchCondition::matchesMenu)
                .filter(searchCondition::matchesTime)
                .filter(searchCondition::matchesPartySize)
                .sorted(searchCondition.comparator())
                .toList();
    }

    private List<Post> findCandidatePostsByVector(
            String condition,
            Long userId,
            Long universityId,
            int userPoint,
            List<Long> sameUniversityUserIds
    ) {
        PostVectorRepository postVectorRepository = postVectorRepositoryProvider == null
                ? null
                : postVectorRepositoryProvider.getIfAvailable();
        if (postVectorRepository == null) {
            return List.of();
        }

        try {
            int topK = Math.max(aiProperties.getMatching().getRag().getTopK(), MAX_RECOMMENDATION_CANDIDATES);
            double threshold = aiProperties.getMatching().getRag().getSimilarityThreshold();
            List<Long> postIds = postVectorRepository.search(
                            condition,
                            universityId,
                            userId,
                            userPoint,
                            topK,
                            threshold
                    )
                    .stream()
                    .map(PostVectorSearchResultDto::postId)
                    .toList();

            // pgvector는 의미 검색과 메타데이터 필터로 postId 후보를 제공하는 보조 인덱스입니다.
            // 최종 추천 대상은 MySQL에서 같은 학교 작성자, OPEN 상태, 미래 약속 시간 조건을 다시 검증합니다.
            return postService.findAiMatchingCandidatePostsByIds(postIds, sameUniversityUserIds);
        } catch (Exception e) {
            log.warn("[AiMatchingTool] 게시글 벡터 검색 실패. MySQL 후보 조회로 대체합니다.", e);
            return List.of();
        }
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
        User user = userService.findByEmail(email);

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
        User user = userService.findByEmail(email);

        Post post = postService.getPostById(postId);

        boolean alreadyApplied =
                matchService.hasAppliedToPost(post.getId(), user.getId());

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
     * - 음식/메뉴 키워드 조건
     * - 날짜 및 시간대 조건
     *
     * RAG 도입 전 단계에서는 Tool Calling에서 후보군 품질을 최대한 높이기 위해
     * 자연어 조건을 서버 측 검색 조건으로 변환하는 역할을 담당합니다.
     */
    private record SearchCondition(
            Sort sort,
            Comparator<Post> comparator,
            List<String> menuKeywords,
            TimeRange timeRange,
            PartySizeCondition partySizeCondition
    ) {

        private static SearchCondition from(String condition) {
            String normalized = normalize(condition);
            Sort sort = resolveSort(normalized);
            Comparator<Post> comparator = resolveComparator(normalized);
            List<String> menuKeywords = resolveMenuKeywords(normalized);
            TimeRange timeRange = resolveTimeRange(normalized);
            PartySizeCondition partySizeCondition = resolvePartySizeCondition(normalized);

            return new SearchCondition(sort, comparator, menuKeywords, timeRange, partySizeCondition);
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

        // 메뉴/음식 키워드 조건과 게시글 내용이 맞는지 확인
        private boolean matchesMenu(Post post) {
            if (menuKeywords.isEmpty()) {
                return true;
            }


            String searchableText = normalize(post.getPlaceName() + " " + post.getContent());

            return menuKeywords.stream().anyMatch(searchableText::contains);
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


        // 자연어 음식 표현을 게시글 검색 키워드로 변환
        private static List<String> resolveMenuKeywords(String normalized) {
            if (containsAny(normalized, "치킨", "닭")) {
                return List.of("치킨", "닭");
            }

            if (containsAny(normalized, "국밥")) {
                return List.of("국밥");
            }

            if (containsAny(normalized, "파스타", "양식")) {
                return List.of("파스타", "양식", "샌드위치", "카페");
            }

            if (containsAny(normalized, "분식", "떡볶이", "김밥")) {
                return List.of("분식", "떡볶이", "김밥");
            }

            if (containsAny(normalized, "샌드위치", "카페")) {
                return List.of("샌드위치", "카페");
            }

            if (containsAny(normalized, "튀김")) {
                return List.of("튀김", "치킨", "돈까스", "분식", "떡볶이");
            }

            return List.of();
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
