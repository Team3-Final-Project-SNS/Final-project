package com.example.team3final.common.init;

import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.university.entity.University;
import com.example.team3final.domain.university.repository.UniversityRepository;
import com.example.team3final.domain.user.entity.TermAgreement;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Profile({"prod", "docker", "local"})
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String DEMO_PLACE_NAME = System.getenv().getOrDefault("DEMO_PLACE_NAME", "AI 추천 테스트 위치");
    private static final BigDecimal DEMO_PLACE_LAT = new BigDecimal(System.getenv().getOrDefault("DEMO_PLACE_LAT", "37.3745300"));
    private static final BigDecimal DEMO_PLACE_LNG = new BigDecimal(System.getenv().getOrDefault("DEMO_PLACE_LNG", "126.6322100"));
    private static final int POSTS_PER_AI_SEED_USER = 50;
    private static final int AI_SEED_USER_BONUS = 10_000;

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final TermAgreementRepository termAgreementRepository;
    private final PostRepository postRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAiMatchingRecommendationPostsIfPossible();
        publishSeedPostVectorEvents();
    }

    private void seedAiMatchingRecommendationPostsIfPossible() {
        University koreaUniversity = getOrCreateUniversity(
                "korea.ac.kr",
                University.builder()
                        .universityName("한국대학교")
                        .eDomain("korea.ac.kr")
                        .isActive(true)
                        .build()
        );
        University naverUniversity = getOrCreateUniversity(
                "naver.com",
                University.builder()
                        .universityName("네이버대학교")
                        .eDomain("naver.com")
                        .isActive(true)
                        .build()
        );
        University googleUniversity = getOrCreateUniversity(
                "gmail.com",
                University.builder()
                        .universityName("구글대학교")
                        .eDomain("gmail.com")
                        .isActive(true)
                        .build()
        );

        User koreaAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-korea@korea.ac.kr",
                "한국AI추천",
                koreaUniversity.getId()
        );
        User naverAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-naver@naver.com",
                "네이버AI추천",
                naverUniversity.getId()
        );
        User googleAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-google@gmail.com",
                "구글AI추천",
                googleUniversity.getId()
        );
        User naverAuthorUser = getOrCreateUser(
                "author@naver.com",
                User.builder()
                        .email("author@naver.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("네이버등록자")
                        .nickname("네이버등록자")
                        .universityId(naverUniversity.getId())
                        .major("시연학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 1, 1))
                        .gender(Gender.MALE)
                        .build()
        );
        User naverApplicantUser = getOrCreateUser(
                "applicant@naver.com",
                User.builder()
                        .email("applicant@naver.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("네이버신청자")
                        .nickname("네이버신청자")
                        .universityId(naverUniversity.getId())
                        .major("시연학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 1, 1))
                        .gender(Gender.FEMALE)
                        .build()
        );
        User naverGuestUser = getOrCreateUser(
                "guest1@naver.com",
                User.builder()
                        .email("guest1@naver.com")
                        .password(passwordEncoder.encode("12345678"))
                        .name("네이버게스트")
                        .nickname("네이버게스트")
                        .universityId(naverUniversity.getId())
                        .major("시연학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 1, 1))
                        .gender(Gender.FEMALE)
                        .build()
        );

        List<User> aiSeedUsers = List.of(koreaAiSeedUser, naverAiSeedUser, googleAiSeedUser);
        List<User> loginSeedUsers = List.of(naverAuthorUser, naverApplicantUser, naverGuestUser);

        aiSeedUsers.forEach(user -> {
            saveTermAgreementIfNotExists(user.getId(), "v1.0");
            giveSignupBonusIfNotExists(user, AI_SEED_USER_BONUS);
        });
        loginSeedUsers.forEach(user -> {
            saveTermAgreementIfNotExists(user.getId(), "v1.0");
            giveSignupBonusIfNotExists(user, AI_SEED_USER_BONUS);
        });

        seedAiMatchingRecommendationPosts(aiSeedUsers);
    }

    private void seedAiMatchingRecommendationPosts(List<User> authors) {
        List<AiMatchingSeedPost> seeds = List.of(
                new AiMatchingSeedPost("후문 치킨집", "치킨이랑 감자튀김", "수다 많고 활발한 분위기", "야식", 300, 4),
                new AiMatchingSeedPost("정문 국밥골목", "국밥", "빠르게 먹고 바로 헤어지는 분위기", "든든한 아침", 500, 2),
                new AiMatchingSeedPost("공대 앞 파스타집", "크림파스타", "조용하고 편한 분위기", "조용한 저녁", 900, 2),
                new AiMatchingSeedPost("학생회관 라멘집", "라멘", "수업 사이 빠른 식사 분위기", "빠른 점심", 400, 2),
                new AiMatchingSeedPost("정문 돈까스집", "돈까스", "처음 봐도 부담 없는 분위기", "가벼운 대화", 500, 3),
                new AiMatchingSeedPost("후문 쌀국수집", "쌀국수", "차분하게 점심 먹는 분위기", "차분한 점심", 600, 2),
                new AiMatchingSeedPost("학생회관 덮밥집", "덮밥", "든든하게 먹고 짧게 헤어지는 분위기", "짧은 점심", 400, 3),
                new AiMatchingSeedPost("중앙광장 분식집", "떡볶이랑 김밥", "편하게 수다 떠는 분위기", "분식", 300, 3),
                new AiMatchingSeedPost("후문 마라탕집", "마라탕", "매운 음식 좋아하는 사람끼리 먹는 분위기", "매운 음식", 700, 3),
                new AiMatchingSeedPost("정문 중국집", "짜장면이나 짬뽕", "빠르게 먹고 헤어져도 괜찮은 분위기", "중식", 400, 2),
                new AiMatchingSeedPost("도서관 샌드위치 카페", "샌드위치", "조용한 브런치 분위기", "브런치", 300, 2),
                new AiMatchingSeedPost("기숙사 컵밥집", "컵밥", "혼밥 피하고 편하게 먹는 분위기", "혼밥 방지", 300, 2),
                new AiMatchingSeedPost("정문 김치찌개집", "김치찌개", "든든하게 저녁 먹는 분위기", "든든한 저녁", 500, 4),
                new AiMatchingSeedPost("후문 초밥집", "초밥", "말수 적어도 괜찮은 분위기", "조용한 식사", 800, 2),
                new AiMatchingSeedPost("공학관 편의점", "삼각김밥이랑 컵라면", "빠르게 밥 먹고 가는 분위기", "편의점 식사", 200, 2),
                new AiMatchingSeedPost("중앙광장 푸드트럭", "타코야끼랑 음료", "들고 가볍게 이야기하는 분위기", "가벼운 간식", 300, 3),
                new AiMatchingSeedPost("후문 부대찌개집", "부대찌개", "든든한 저녁을 나누는 분위기", "찌개", 600, 4),
                new AiMatchingSeedPost("정문 샐러드집", "샐러드랑 샌드위치", "가볍게 먹는 분위기", "가벼운 식사", 400, 2),
                new AiMatchingSeedPost("학생회관 한식코너", "백반", "조용히 먹고 각자 할 일 하러 가는 분위기", "한식", 400, 2),
                new AiMatchingSeedPost("후문 카레집", "카레", "어색하지 않게 편하게 대화하는 분위기", "편한 대화", 500, 3),
                new AiMatchingSeedPost("기숙사 치킨포차", "치킨이랑 콜라", "활발하게 떠드는 야식 분위기", "늦은 밤 야식", 800, 5),
                new AiMatchingSeedPost("정문 칼국수집", "칼국수", "따뜻하고 차분한 분위기", "따뜻한 한 끼", 400, 2),
                new AiMatchingSeedPost("후문 제육덮밥집", "제육덮밥", "든든하게 먹고 바로 헤어지는 분위기", "든든한 한 끼", 400, 2),
                new AiMatchingSeedPost("공대 브런치카페", "파스타나 샌드위치", "점심 겸 브런치 분위기", "브런치", 700, 2),
                new AiMatchingSeedPost("학생회관 김밥집", "김밥이랑 라면", "부담 없이 간단하게 먹는 분위기", "간단한 식사", 300, 3),
                new AiMatchingSeedPost("중앙 짬뽕집", "짬뽕", "말 많지 않아도 괜찮은 분위기", "얼큰한 점심", 500, 2),
                new AiMatchingSeedPost("후문 파스타바", "토마토파스타", "천천히 이야기하는 분위기", "느긋한 저녁", 900, 2),
                new AiMatchingSeedPost("정문 라멘야", "라멘", "카페 없이 바로 헤어지는 분위기", "빠른 라멘", 500, 2),
                new AiMatchingSeedPost("기숙사 국밥집", "국밥", "든든하게 먹고 힘내는 분위기", "든든한 식사", 600, 3),
                new AiMatchingSeedPost("후문 돈까스골목", "치즈돈까스", "재밌게 이야기하면서 먹는 분위기", "즐거운 대화", 600, 3),
                new AiMatchingSeedPost("정문 베트남식당", "쌀국수랑 볶음밥", "조용하게 먹어도 좋은 분위기", "베트남 음식", 600, 2),
                new AiMatchingSeedPost("학생회관 오므라이스집", "오므라이스", "수업 사이 빠르게 먹는 분위기", "빠른 점심", 400, 2),
                new AiMatchingSeedPost("후문 야식분식", "떡볶이랑 튀김", "수다 많은 야식 분위기", "야식 분식", 500, 4),
                new AiMatchingSeedPost("공대 앞 중식당", "짜장면 탕수육", "여럿이 나눠 먹는 분위기", "중식 나눔", 800, 5),
                new AiMatchingSeedPost("도서관 앞 죽집", "죽이나 가벼운 한식", "조용한 식사 분위기", "가벼운 한식", 300, 2),
                new AiMatchingSeedPost("정문 햄버거집", "햄버거 세트", "빠르게 먹고 헤어지는 분위기", "빠른 식사", 400, 2),
                new AiMatchingSeedPost("후문 브리또집", "브리또나 타코", "재밌게 이야기하는 분위기", "멕시칸", 500, 3),
                new AiMatchingSeedPost("학생회관 찌개코너", "된장찌개", "혼자 먹기 싫을 때 든든하게 먹는 분위기", "찌개", 400, 3)
        );

        LocalDateTime base = LocalDateTime.now().plusMinutes(30);

        for (User author : authors) {
            for (int i = 0; i < POSTS_PER_AI_SEED_USER; i++) {
                AiMatchingSeedPost seed = seeds.get(i % seeds.size());
                int cycle = i / seeds.size();
                int sequence = i + 1;
                LocalDateTime meetAt = base
                        .plusDays(cycle / 2)
                        .plusMinutes((long) sequence * 13);
                int deposit = seed.deposit() + (cycle % 3) * 100;
                int maxApplicants = Math.min(5, Math.max(2, seed.maxApplicants() + (cycle % 2)));
                String content = "%s 먹을 분 구해요. %s 선호해요. %s"
                        .formatted(
                                seed.menu(),
                                seed.atmosphere(),
                                aiMatchingSeedContentVariation(cycle, seed.trimKeyword())
                        );

                if (existsPostByAuthorAndContent(author.getId(), content)) {
                    continue;
                }

                postRepository.save(
                        Post.builder()
                                .authorId(author.getId())
                                .meetAt(meetAt)
                                .placeName(seed.placeName())
                                .placeLat(DEMO_PLACE_LAT)
                                .placeLng(DEMO_PLACE_LNG)
                                .content(content)
                                .authorDeposit(deposit)
                                .maxApplicants(maxApplicants)
                                .build()
                );
            }
        }
    }

    private String aiMatchingSeedContentVariation(int cycle, String trimKeyword) {
        return switch (cycle % 4) {
            case 1 -> "%s 메뉴로 시간 맞으면 가볍게 신청해주세요.".formatted(trimKeyword);
            case 2 -> "%s 생각날 때 혼자 먹기 애매해서 같이 먹을 분 찾습니다.".formatted(trimKeyword);
            case 3 -> "%s 분위기로 처음 보는 사람이어도 부담 없는 식사면 좋겠습니다.".formatted(trimKeyword);
            default -> "%s 느낌으로 편하게 한 끼 같이 먹으면 좋겠습니다.".formatted(trimKeyword);
        };
    }

    private void publishSeedPostVectorEvents() {
        postRepository.findByStatusAndMeetAtAfter(PostStatus.OPEN, LocalDateTime.now())
                .forEach(post -> userRepository.findById(post.getAuthorId())
                        .ifPresent(author -> applicationEventPublisher.publishEvent(
                                new PostVectorUpsertEvent(
                                        post.getId(),
                                        post.getAuthorId(),
                                        author.getUniversityId(),
                                        post.getStatus(),
                                        post.getMeetAt(),
                                        post.getPlaceName(),
                                        post.getContent(),
                                        post.getAuthorDeposit(),
                                        post.getMaxApplicants(),
                                        post.getCurrentApplicants(),
                                        post.getPlaceLat(),
                                        post.getPlaceLng()
                                )
                        )));
    }

    private University getOrCreateUniversity(String eDomain, University university) {
        return universityRepository.findByeDomainAndIsActiveTrue(eDomain)
                .orElseGet(() -> universityRepository.save(university));
    }

    private User getOrCreateAiMatchingSeedUser(String email, String nickname, Long universityId) {
        return getOrCreateUser(
                email,
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("password123!"))
                        .name(nickname)
                        .nickname(nickname)
                        .universityId(universityId)
                        .major("AI추천학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 1, 1))
                        .gender(Gender.MALE)
                        .build()
        );
    }

    private User getOrCreateUser(String email, User user) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(user));
    }

    private void saveTermAgreementIfNotExists(Long userId, String termVersion) {
        boolean exists = termAgreementRepository.findAll().stream()
                .anyMatch(termAgreement ->
                        userId.equals(termAgreement.getUserId())
                                && termVersion.equals(termAgreement.getTermVersion()));

        if (exists) {
            return;
        }

        termAgreementRepository.save(
                TermAgreement.builder()
                        .userId(userId)
                        .termVersion(termVersion)
                        .build()
        );
    }

    private void giveSignupBonusIfNotExists(User user, int amount) {
        boolean exists = pointTransactionRepository.findAll().stream()
                .anyMatch(pointTransaction ->
                        user.getId().equals(pointTransaction.getUserId())
                                && pointTransaction.getTransactionType() == PointTransactionType.JOIN_BONUS);

        if (exists) {
            return;
        }

        user.addFreePoint(amount);
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(user.getId())
                        .amount(amount)
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(user.getTotalPoint())
                        .pointSource(PointSource.FREE)
                        .description("AI 추천 seed 유저 가입 보너스")
                        .build()
        );
    }

    private boolean existsPostByAuthorAndContent(Long authorId, String content) {
        return postRepository.findAll().stream()
                .anyMatch(post ->
                        authorId.equals(post.getAuthorId())
                                && content.equals(post.getContent()));
    }

    private record AiMatchingSeedPost(
            String placeName,
            String menu,
            String atmosphere,
            String trimKeyword,
            int deposit,
            int maxApplicants
    ) {
    }
}
