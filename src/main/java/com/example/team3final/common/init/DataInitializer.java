package com.example.team3final.common.init;

import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointSource;
import com.example.team3final.domain.pointTransaction.enums.PointReferenceType;
import com.example.team3final.domain.pointTransaction.enums.PointSettlementReason;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
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

    private static final String DEMO_PLACE_NAME = System.getenv().getOrDefault("DEMO_PLACE_NAME", "장소 인증 테스트 위치");
    private static final BigDecimal DEMO_PLACE_LAT = new BigDecimal(System.getenv().getOrDefault("DEMO_PLACE_LAT", "37.3745300"));
    private static final BigDecimal DEMO_PLACE_LNG = new BigDecimal(System.getenv().getOrDefault("DEMO_PLACE_LNG", "126.6322100"));

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final TermAgreementRepository termAgreementRepository;
    private final PostRepository postRepository;
    private final MatchRepository matchRepository;
    private final MeetVerificationRepository meetVerificationRepository;
    private final UserLocationRepository userLocationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatMemberRepository chatMemberRepository;
    private final ReportRepository reportRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 이미 대표 seed 게시글이 있으면 local 이닛데이터를 다시 만들지 않습니다.
        // 서버 재시작 때마다 중복 데이터가 쌓이는 것을 막기 위한 방어 코드입니다.
        if (existsPostByContent("1:N 리뷰 테스트용 완료된 단체 식사입니다.")) {
            seedAiMatchingRecommendationPostsIfPossible();
            seedAdminAiReviewDataIfPossible();
            seedNoShowDisputeScenarioDataIfPossible();
            publishSeedPostVectorEvents();
            return;
        }

        // ===================================================
        // 1. 대학교 생성
        // ===================================================
        University university = getOrCreateUniversity(
                "korea.ac.kr",
                University.builder()
                        .universityName("한국대학교")
                        .eDomain("korea.ac.kr")
                        .isActive(true)
                        .build()
        );

        University university1 = getOrCreateUniversity(
                "naver.com",
                University.builder()
                        .universityName("네이버대학교")
                        .eDomain("naver.com")
                        .isActive(true)
                        .build()
        );

        University googleUniversity = getOrCreateUniversity(
                "google.com",
                University.builder()
                        .universityName("구글대학교")
                        .eDomain("google.com")
                        .isActive(true)
                        .build()
        );

        // ===================================================
        // 2. 유저 생성 (초기 생성 시 point는 자동으로 0 세팅됨)
        // ===================================================
        User author = getOrCreateUser(
                "author@korea.ac.kr",
                User.builder()
                        .email("author@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!"))
                        .name("김등록")
                        .nickname("밥먹자")
                        .universityId(university.getId())
                        .major("컴퓨터공학과")
                        .studentNumber("24")
                        .birthDate(LocalDate.of(2004, 3, 15))
                        .gender(Gender.MALE)
                        .build()
        );

        User applicant = getOrCreateUser(
                "applicant@korea.ac.kr",
                User.builder()
                        .email("applicant@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!"))
                        .name("이신청")
                        .nickname("같이밥먹어요")
                        .universityId(university.getId())
                        .major("경영학과")
                        .studentNumber("23")
                        .birthDate(LocalDate.of(2003, 7, 22))
                        .gender(Gender.FEMALE)
                        .build()
        );

        User hacker = getOrCreateUser(
                "dalsun_rin@naver.com",
                User.builder()
                        .email("dalsun_rin@naver.com")
                        .password(passwordEncoder.encode("ansgpfls79"))
                        .name("나해커")
                        .nickname("정당한참여자아님")
                        .universityId(university1.getId())
                        .major("법학과")
                        .studentNumber("22")
                        .birthDate(LocalDate.of(2002, 1, 1))
                        .gender(Gender.MALE)
                        .build()
        );

        // ===================================================
        // 2-1. 네이버대학교 1:N 리뷰 테스트용 유저 생성
        // ===================================================
        User naverAuthor = getOrCreateUser(
                "naver-author@naver.com",
                User.builder()
                        .email("naver-author@naver.com")
                        .password(passwordEncoder.encode("password123!"))
                        .name("최등록")
                        .nickname("네이버밥장")
                        .universityId(university1.getId())
                        .major("컴퓨터공학과")
                        .studentNumber("21")
                        .birthDate(LocalDate.of(2001, 5, 10))
                        .gender(Gender.MALE)
                        .build()
        );

        User naverApplicant1 = getOrCreateUser(
                "naver-applicant1@naver.com",
                User.builder()
                        .email("naver-applicant1@naver.com")
                        .password(passwordEncoder.encode("password123!"))
                        .name("박신청")
                        .nickname("네이버신청1")
                        .universityId(university1.getId())
                        .major("경영학과")
                        .studentNumber("23")
                        .birthDate(LocalDate.of(2003, 2, 12))
                        .gender(Gender.FEMALE)
                        .build()
        );

        User naverApplicant2 = getOrCreateUser(
                "naver-applicant2@naver.com",
                User.builder()
                        .email("naver-applicant2@naver.com")
                        .password(passwordEncoder.encode("password123!"))
                        .name("정신청")
                        .nickname("네이버신청2")
                        .universityId(university1.getId())
                        .major("소프트웨어학과")
                        .studentNumber("24")
                        .birthDate(LocalDate.of(2004, 8, 21))
                        .gender(Gender.MALE)
                        .build()
        );

        User naverApplicant3 = getOrCreateUser(
                "naver-applicant3@naver.com",
                User.builder()
                        .email("naver-applicant3@naver.com")
                        .password(passwordEncoder.encode("password123!"))
                        .name("윤신청")
                        .nickname("네이버신청3")
                        .universityId(university1.getId())
                        .major("디자인학과")
                        .studentNumber("22")
                        .birthDate(LocalDate.of(2002, 11, 3))
                        .gender(Gender.FEMALE)
                        .build()
        );

        User koreaAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-korea@korea.ac.kr",
                "한국AI추천",
                university.getId()
        );
        User naverAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-naver@naver.com",
                "네이버AI추천",
                university1.getId()
        );
        User googleAiSeedUser = getOrCreateAiMatchingSeedUser(
                "ai-seed-google@google.com",
                "구글AI추천",
                googleUniversity.getId()
        );

        // ===================================================
        // 3. 약관 동의 이력 생성
        // ===================================================
        saveTermAgreementIfNotExists(author.getId(), "v1.0");
        saveTermAgreementIfNotExists(applicant.getId(), "v1.0");
        saveTermAgreementIfNotExists(hacker.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverAuthor.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverApplicant1.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverApplicant2.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverApplicant3.getId(), "v1.0");
        saveTermAgreementIfNotExists(koreaAiSeedUser.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverAiSeedUser.getId(), "v1.0");
        saveTermAgreementIfNotExists(googleAiSeedUser.getId(), "v1.0");

        // ===================================================
        // 4. 포인트 가입 보너스 지급 (기본 10,000포인트 충전)
        // ===================================================
        int authorBonus = 10000;
        int applicantBonus = 10000;
        int hackerBonus = 10000;
        int naverAuthorBonus = 10000;
        int naverApplicant1Bonus = 10000;
        int naverApplicant2Bonus = 10000;
        int naverApplicant3Bonus = 10000;
        int aiSeedUserBonus = 10000;

        giveSignupBonusIfNotExists(author, authorBonus);
        giveSignupBonusIfNotExists(applicant, applicantBonus);
        giveSignupBonusIfNotExists(hacker, hackerBonus);
        giveSignupBonusIfNotExists(naverAuthor, naverAuthorBonus);
        giveSignupBonusIfNotExists(naverApplicant1, naverApplicant1Bonus);
        giveSignupBonusIfNotExists(naverApplicant2, naverApplicant2Bonus);
        giveSignupBonusIfNotExists(naverApplicant3, naverApplicant3Bonus);
        giveSignupBonusIfNotExists(koreaAiSeedUser, aiSeedUserBonus);
        giveSignupBonusIfNotExists(naverAiSeedUser, aiSeedUserBonus);
        giveSignupBonusIfNotExists(googleAiSeedUser, aiSeedUserBonus);

        // ===================================================
        // NAVER CASE A. 네이버대학교 OPEN 게시글 2개
        // - naver.com 계정으로 로그인했을 때 학교 필터가 정상 동작하는지 확인합니다.
        // ===================================================
        postRepository.save(
                Post.builder()
                        .authorId(naverAuthor.getId())
                        .meetAt(LocalDateTime.now().plusHours(2))
                        .placeName("네이버대 학생회관")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("네이버대 학생회관에서 점심 같이 먹을 분 구해요.")
                        .authorDeposit(400)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(naverApplicant1.getId())
                        .meetAt(LocalDateTime.now().plusHours(3))
                        .placeName("네이버대 후문 분식집")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("후문 분식집에서 가볍게 밥 먹을 분 찾아요.")
                        .authorDeposit(500)
                        .maxApplicants(2)
                        .build()
        );

        // ===================================================
        // NAVER CASE B. 1:N 리뷰 테스트용 완료 만남
        // - 등록자 1명 + 신청자 3명
        // - 신청자 계정으로 리뷰 작성 API를 바로 테스트할 수 있도록 Match를 COMPLETED 상태로 만듭니다.
        // - 리뷰 정책상 등록자는 리뷰 작성 불가, 신청자 리뷰 평균이 등록자 매너온도에 반영됩니다.
        // ===================================================
        Post naverGroupCompletedPost = postRepository.save(
                Post.builder()
                        .authorId(naverAuthor.getId())
                        .meetAt(LocalDateTime.now().minusHours(2))
                        .placeName("네이버대 중앙식당")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("1:N 리뷰 테스트용 완료된 단체 식사입니다.")
                        .authorDeposit(600)
                        .maxApplicants(5)
                        .build()
        );

        Match naverGroupMatch1 = matchRepository.save(
                Match.builder()
                        .postId(naverGroupCompletedPost.getId())
                        .applicantId(naverApplicant1.getId())
                        .applicantDeposit(600)
                        .build()
        );

        Match naverGroupMatch2 = matchRepository.save(
                Match.builder()
                        .postId(naverGroupCompletedPost.getId())
                        .applicantId(naverApplicant2.getId())
                        .applicantDeposit(600)
                        .build()
        );

        Match naverGroupMatch3 = matchRepository.save(
                Match.builder()
                        .postId(naverGroupCompletedPost.getId())
                        .applicantId(naverApplicant3.getId())
                        .applicantDeposit(600)
                        .build()
        );

        // 단체 게시글 참여 인원과 상태를 테스트 목적에 맞게 완료 상태로 맞춥니다.
        naverGroupCompletedPost.increaseCurrentApplicants();
        naverGroupCompletedPost.increaseCurrentApplicants();
        naverGroupCompletedPost.increaseCurrentApplicants();
        naverGroupCompletedPost.match();
        naverGroupCompletedPost.complete();

        // 리뷰 작성 가능 조건이 MatchStatus.COMPLETED 이므로 각 신청자의 매칭을 완료 상태로 만듭니다.
        naverGroupMatch1.complete();
        naverGroupMatch2.complete();
        naverGroupMatch3.complete();

        saveMeetVerificationIfNotExists(naverGroupMatch1.getId());
        saveMeetVerificationIfNotExists(naverGroupMatch2.getId());
        saveMeetVerificationIfNotExists(naverGroupMatch3.getId());

        ChatRoom naverGroupChatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                        .postId(naverGroupCompletedPost.getId())
                        .roomType(ChatRoomType.GROUP)
                        .build()
        );

        saveChatMemberIfNotExists(naverGroupChatRoom.getId(), naverAuthor.getId(), ChatMemberRole.HOST);
        saveChatMemberIfNotExists(naverGroupChatRoom.getId(), naverApplicant1.getId(), ChatMemberRole.GUEST);
        saveChatMemberIfNotExists(naverGroupChatRoom.getId(), naverApplicant2.getId(), ChatMemberRole.GUEST);
        saveChatMemberIfNotExists(naverGroupChatRoom.getId(), naverApplicant3.getId(), ChatMemberRole.GUEST);

        chatMessageRepository.save(ChatMessage.builder().chatRoomId(naverGroupChatRoom.getId()).senderId(naverAuthor.getId()).content("단체 식사 리뷰 테스트용 채팅방입니다.").build());

        // ===================================================
        // CASE A. 활성화 상태의 매칭 (MATCHED)
        // ===================================================
        Post activePost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusMinutes(10))
                        .placeName(DEMO_PLACE_NAME)
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("활성화된 채팅 및 인증 테스트용 방입니다.")
                        .authorDeposit(300)
                        .build()
        );
        activePost.match();

        Match activeMatch = matchRepository.save(
                Match.builder()
                        .postId(activePost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );
        // CASE A: 게시글 작성이므로 방장(author) 책임비 예치금 차감 적용
        author.deduct(300);

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(activeMatch.getId())
                        .referenceType(PointReferenceType.POST)
                        .referenceId(activePost.getId())
                        .amount(-300)
                        .transactionType(PointTransactionType.DEPOSIT)
                        .balanceAfter(authorBonus - 300)
                        .pointSource(PointSource.FREE)
                        .description("게시글 작성 책임비 예치")
                        .build()
        );

        saveMeetVerificationIfNotExists(activeMatch.getId());

        saveUserLocationIfNotExists(activeMatch.getId(), author.getId());
        saveUserLocationIfNotExists(activeMatch.getId(), applicant.getId());

        ChatRoom activeChatRoom = chatRoomRepository.save(ChatRoom.builder().postId(activePost.getId()).build());

        saveChatMemberIfNotExists(activeChatRoom.getId(), author.getId(), ChatMemberRole.HOST);
        saveChatMemberIfNotExists(activeChatRoom.getId(), applicant.getId(), ChatMemberRole.GUEST);

        ChatMessage msg1 = chatMessageRepository.save(ChatMessage.builder().chatRoomId(activeChatRoom.getId()).senderId(author.getId()).content("안녕하세요!").build());
        msg1.markAsRead();
        ChatMessage msg2 = chatMessageRepository.save(ChatMessage.builder().chatRoomId(activeChatRoom.getId()).senderId(applicant.getId()).content("네 반갑습니다.").build());
        msg2.markAsRead();
        chatMessageRepository.save(ChatMessage.builder().chatRoomId(activeChatRoom.getId()).senderId(author.getId()).content("안 읽은 메시지 테스트용").build());

        // ===================================================
        // CASE B. 비활성화/종료된 매칭 (COMPLETED)
        // ===================================================
        Post completedPost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().minusDays(1))
                        .placeName("학생회관 지하")
                        .placeLat(new BigDecimal("37.3740000"))
                        .placeLng(new BigDecimal("126.6320000"))
                        .content("이미 완료된 약속입니다.")
                        .authorDeposit(300)
                        .build()
        );

        Match completedMatch = matchRepository.save(
                Match.builder()
                        .postId(completedPost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        ChatRoom completedChatRoom = chatRoomRepository.save(ChatRoom.builder().postId(completedPost.getId()).build());

        saveChatMemberIfNotExists(completedChatRoom.getId(), author.getId(), ChatMemberRole.HOST);
        saveChatMemberIfNotExists(completedChatRoom.getId(), applicant.getId(), ChatMemberRole.GUEST);

        chatMessageRepository.save(ChatMessage.builder().chatRoomId(completedChatRoom.getId()).senderId(author.getId()).content("예전 완료된 대화내용입니다.").build());

        // 원래는 완료되면서 차감되었다가 환급(REFUND)된 케이스이므로 결과적으로 잔액 변동 없음
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(completedMatch.getId())
                        .referenceType(PointReferenceType.POST)
                        .referenceId(completedPost.getId())
                        .settlementReason(PointSettlementReason.AUTHOR_DEPOSIT)
                        .amount(300)
                        .transactionType(PointTransactionType.REFUND)
                        .balanceAfter(authorBonus)
                        .pointSource(PointSource.PAID)
                        .description("만남 인증 완료로 인한 책임비 환급")
                        .build()
        );

        // ===================================================
        // CASE C. 취소된 매칭 (CANCELLED)
        // ===================================================
        Post cancelledPost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(5))
                        .placeName("도서관 앞")
                        .placeLat(new BigDecimal("37.3750000"))
                        .placeLng(new BigDecimal("126.6350000"))
                        .content("취소 처리된 게시글 시뮬레이션")
                        .authorDeposit(300)
                        .build()
        );

        Match cancelledMatch = matchRepository.save(
                Match.builder()
                        .postId(cancelledPost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        // CASE C: 당일 취소로 인한 지원자(applicant)의 패널티 차감 적용
        applicant.deduct(500);

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .matchId(cancelledMatch.getId())
                        .referenceType(PointReferenceType.MATCH)
                        .referenceId(cancelledMatch.getId())
                        .settlementReason(PointSettlementReason.APPLICANT_DEPOSIT)
                        .amount(-500)
                        .transactionType(PointTransactionType.PENALTY)
                        .balanceAfter(applicantBonus - 500)
                        .pointSource(PointSource.FREE)
                        .description("당일 취소로 인한 패널티 차감")
                        .build()
        );

        // ===================================================
        // CASE D. OPEN 상태 게시글
        // ===================================================
        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusDays(1))
                        .placeName("중앙도서관 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("강제 삭제 테스트용 게시글입니다.")
                        .authorDeposit(300)
                        .build()
        );

        // ===================================================
// AI 매칭 추천 테스트용 OPEN 게시글 10개
// - hacker@korea.ac.kr 로 로그인해서 추천 테스트
// - 작성자는 author/applicant
// - 상태는 OPEN 유지
// ===================================================
        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(1))
                        .placeName("학생회관 1층")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("오늘 저녁 조용하게 밥 먹을 분 구합니다. 말수가 적어도 괜찮아요.")
                        .authorDeposit(500)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(2))
                        .placeName("정문 편의점 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("가볍게 저녁 먹고 빠르게 헤어질 분 찾아요.")
                        .authorDeposit(300)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(3))
                        .placeName("도서관 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("스터디 끝나고 조용히 식사하실 분 구해요.")
                        .authorDeposit(400)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(4))
                        .placeName("후문 국밥집 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("든든하게 저녁 먹을 분 찾습니다. 메뉴는 국밥 생각 중입니다.")
                        .authorDeposit(700)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusHours(5))
                        .placeName("공대 카페 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("카페에서 간단히 샌드위치 먹으면서 이야기하실 분.")
                        .authorDeposit(300)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(applicant.getId())
                        .meetAt(LocalDateTime.now().plusHours(1).plusMinutes(30))
                        .placeName("기숙사 식당 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("혼밥하기 애매해서 같이 점심 드실 분 구합니다.")
                        .authorDeposit(400)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(applicant.getId())
                        .meetAt(LocalDateTime.now().plusHours(2).plusMinutes(30))
                        .placeName("학생회관 분식집")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("떡볶이랑 김밥 가볍게 먹을 분 찾아요. 편한 분위기 좋아요.")
                        .authorDeposit(300)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(applicant.getId())
                        .meetAt(LocalDateTime.now().plusHours(3).plusMinutes(30))
                        .placeName("정문 파스타집")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("천천히 이야기하면서 저녁 먹을 분 구합니다. 너무 시끄럽지 않은 곳 선호해요.")
                        .authorDeposit(800)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(applicant.getId())
                        .meetAt(LocalDateTime.now().plusHours(4).plusMinutes(30))
                        .placeName("중앙광장")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("수업 끝나고 간단하게 밥 먹고 바로 헤어질 분.")
                        .authorDeposit(300)
                        .maxApplicants(2)
                        .build()
        );

        postRepository.save(
                Post.builder()
                        .authorId(applicant.getId())
                        .meetAt(LocalDateTime.now().plusHours(6))
                        .placeName("후문 치킨집")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("저녁에 치킨 먹을 분 구해요. 활발하게 대화하는 분위기 괜찮습니다.")
                        .authorDeposit(900)
                        .maxApplicants(2)
                        .build()
        );

        seedAiMatchingRecommendationPosts(List.of(koreaAiSeedUser, naverAiSeedUser, googleAiSeedUser));

        // ===================================================
        // CASE E. 노쇼 상태 MeetVerification
        // ===================================================
        Post noShowPost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().minusHours(1))
                        .placeName("공학관 앞")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("노쇼 판정 테스트용 게시글")
                        .authorDeposit(300)
                        .build()
        );
        noShowPost.match();

        Match noShowMatch = matchRepository.save(
                Match.builder()
                        .postId(noShowPost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        MeetVerification noShowVerification = MeetVerification.createPending(noShowMatch.getId());
        noShowVerification.markAuthorNoShow();
        meetVerificationRepository.save(noShowVerification);

        // 변경된 포인트(Dirty Checking)가 DB에 확실히 저장되도록 유저 정보 최종 저장
        userRepository.save(author);
        userRepository.save(applicant);
        userRepository.save(hacker);
        userRepository.save(naverAuthor);
        userRepository.save(naverApplicant1);
        userRepository.save(naverApplicant2);
        userRepository.save(naverApplicant3);

        // ===================================================
        // CASE F. 이의제기 "성공" 케이스
        //         노쇼 상태 + 양측 GPS 진입 완료
        //         → POST /api/v1/matches/{F-matchId}/disputes 가 201 로 성공해야 함
        // ===================================================
        Post disputeOkPost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().minusHours(1)) // 약속시간 지남(노쇼 맥락)
                        .placeName("이의제기 성공 테스트 장소")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("이의제기 성공 케이스용 (노쇼 + GPS 완료)")
                        .authorDeposit(300)
                        .build()
        );
        disputeOkPost.match(); // 게시글 상태 MATCHED 로

        Match disputeOkMatch = matchRepository.save(
                Match.builder()
                        .postId(disputeOkPost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        MeetVerification disputeOkVerification = MeetVerification.createPending(disputeOkMatch.getId());
        // (1) GPS 진입 먼저 — 양측 모두 약속장소 반경 진입 처리
        disputeOkVerification.verifyAuthorPlace();      // authorPlaceVerifiedAt = now (→ 내부에서 VERIFIED 시도)
        disputeOkVerification.verifyApplicantPlace();   // applicantPlaceVerifiedAt = now (→ 여기서 VERIFIED 됨)
        // (2) 그 다음 노쇼 판정 — 상태를 BOTH_NO_SHOW 로 덮어씀 (양측 노쇼 시나리오)
        disputeOkVerification.markBothNoShow();
        meetVerificationRepository.save(disputeOkVerification);

        // ===================================================
        // CASE G. 이의제기 "실패" 케이스 — 노쇼가 아님(VERIFIED)
        //         → DISPUTE_001(노쇼 예정 상태 아님, 422) 확인용
        // ===================================================
        Post notNoShowPost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusMinutes(30))
                        .placeName("이의제기 실패(비노쇼) 테스트 장소")
                        .placeLat(DEMO_PLACE_LAT)
                        .placeLng(DEMO_PLACE_LNG)
                        .content("이의제기 실패 케이스용 (노쇼 아님 / VERIFIED)")
                        .authorDeposit(300)
                        .build()
        );
        notNoShowPost.match();

        Match notNoShowMatch = matchRepository.save(
                Match.builder()
                        .postId(notNoShowPost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        MeetVerification verifiedOnly = MeetVerification.createPending(notNoShowMatch.getId());
        // 양측 GPS 만 찍어 VERIFIED 상태로 둔다 (노쇼 판정은 하지 않음)
        verifiedOnly.verifyAuthorPlace();
        verifiedOnly.verifyApplicantPlace(); // 여기서 자동으로 VERIFIED
        meetVerificationRepository.save(verifiedOnly);

        seedAdminAiReviewData(author, applicant, hacker);
        seedAdminAiPaymentData(author, applicant, hacker);
        seedNoShowDisputeScenarioDataIfPossible();

        publishSeedPostVectorEvents();
    }

    /**
     * 대표 게시글이 이미 있으면 같은 local seed를 다시 만들지 않기 위한 확인 메서드입니다.
     */
    private boolean existsPostByContent(String content) {
        return postRepository.findAll().stream()
                .anyMatch(post -> content.equals(post.getContent()));
    }

    private void seedAdminAiReviewDataIfPossible() {
        User author = userRepository.findByEmail("author@korea.ac.kr").orElse(null);
        User applicant = userRepository.findByEmail("applicant@korea.ac.kr").orElse(null);
        User hacker = userRepository.findByEmail("dalsun_rin@naver.com").orElse(null);

        if (author == null || applicant == null || hacker == null) {
            return;
        }

        seedAdminAiReviewData(author, applicant, hacker);
        seedAdminAiPaymentData(author, applicant, hacker);
    }

    private void seedNoShowDisputeScenarioDataIfPossible() {
        University university = getOrCreateUniversity(
                "korea.ac.kr",
                University.builder()
                        .universityName("한국대학교")
                        .eDomain("korea.ac.kr")
                        .isActive(true)
                        .build()
        );

        User author = getOrCreateUser(
                "dsp-author@korea.ac.kr",
                User.builder()
                        .email("dsp-author@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!"))
                        .name("노쇼등록")
                        .nickname("DSP등록자")
                        .universityId(university.getId())
                        .major("테스트학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 4, 1))
                        .gender(Gender.MALE)
                        .build()
        );
        User applicant = getOrCreateUser(
                "dsp-applicant@korea.ac.kr",
                User.builder()
                        .email("dsp-applicant@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!"))
                        .name("노쇼신청")
                        .nickname("DSP신청자")
                        .universityId(university.getId())
                        .major("테스트학과")
                        .studentNumber("25")
                        .birthDate(LocalDate.of(2005, 5, 1))
                        .gender(Gender.FEMALE)
                        .build()
        );

        saveTermAgreementIfNotExists(author.getId(), "v1.0");
        saveTermAgreementIfNotExists(applicant.getId(), "v1.0");
        giveSignupBonusIfNotExists(author, 10000);
        giveSignupBonusIfNotExists(applicant, 10000);

        seedNoShowDisputeScenarioData(author, applicant);
    }

    private void seedNoShowDisputeScenarioData(User author, User applicant) {
        seedNoShowDisputeScenario(
                author,
                applicant,
                "DSP-SEED-46 제출 전: 신청자 노쇼 예정 상태에서 이의제기 제출 테스트",
                "DSP-01 제출 전 테스트 식당",
                VerificationStatus.GUEST_NO_SHOW,
                null
        );
        seedNoShowDisputeScenario(
                author,
                applicant,
                "DSP-SEED-47A 관리자 ACCEPTED 판정용 SUBMITTED 이의제기",
                "ADM-DSP ACCEPTED 테스트 식당",
                VerificationStatus.GUEST_NO_SHOW,
                DisputeSeedStatus.SUBMITTED
        );
        seedNoShowDisputeScenario(
                author,
                applicant,
                "DSP-SEED-47B 관리자 REJECTED 판정용 SUBMITTED 이의제기",
                "ADM-DSP REJECTED 테스트 식당",
                VerificationStatus.GUEST_NO_SHOW,
                DisputeSeedStatus.SUBMITTED
        );
        seedNoShowDisputeScenario(
                author,
                applicant,
                "DSP-SEED-47C 관리자 HOLD 판정용 SUBMITTED 이의제기",
                "ADM-DSP HOLD 테스트 식당",
                VerificationStatus.GUEST_NO_SHOW,
                DisputeSeedStatus.SUBMITTED
        );
        seedNoShowDisputeScenario(
                author,
                applicant,
                "DSP-SEED-48 HOLD 재제출 테스트용 원본 이의제기",
                "DSP-03 HOLD 재제출 테스트 식당",
                VerificationStatus.GUEST_NO_SHOW,
                DisputeSeedStatus.HOLD
        );
    }

    private void seedNoShowDisputeScenario(
            User author,
            User applicant,
            String content,
            String placeName,
            VerificationStatus noShowStatus,
            DisputeSeedStatus disputeSeedStatus
    ) {
        Post post = getOrCreateSeedPostByContent(
                author,
                content,
                placeName,
                LocalDateTime.now().minusHours(1),
                300
        );
        if (post.isOpen()) {
            post.match();
        }

        Match match = getOrCreateSeedMatch(post, applicant, 300);

        MeetVerification verification = meetVerificationRepository.findByMatchId(match.getId())
                .orElseGet(() -> {
                    MeetVerification created = MeetVerification.createPending(match.getId());
                    markNoShowStatus(created, noShowStatus);
                    return meetVerificationRepository.save(created);
                });

        if (disputeSeedStatus == null) {
            saveDisputeScenarioChatMessages(post, author, applicant, "DSP-01 제출 전 상태입니다.");
            return;
        }

        if (!disputeRepository.existsByMatchIdAndSubmitterId(match.getId(), applicant.getId())) {
            if (verification.getStatus() != VerificationStatus.DISPUTE) {
                verification.markDispute();
            }
            if (match.getStatus().name().equals("MATCHED")) {
                match.dispute();
            }

            Dispute dispute = disputeRepository.save(
                    Dispute.builder()
                            .matchId(match.getId())
                            .submitterId(applicant.getId())
                            .disputeType(DisputeType.GPS_ERROR)
                            .reason("%s - GPS 인증 오류로 노쇼 예정 상태가 되어 이의제기합니다.".formatted(content))
                            .evidenceUrl("https://example.com/dsp-seed-evidence.png")
                            .parentDisputeId(null)
                            .build()
            );

            if (disputeSeedStatus == DisputeSeedStatus.HOLD) {
                dispute.startReview(1L);
                dispute.hold(1L, "DSP-03 재제출 테스트용 HOLD seed입니다. 같은 유형으로 재제출하세요.");
            }
        }

        saveDisputeScenarioChatMessages(post, author, applicant, content);
    }

    private void markNoShowStatus(MeetVerification verification, VerificationStatus noShowStatus) {
        if (noShowStatus == VerificationStatus.HOST_NO_SHOW) {
            verification.markAuthorNoShow();
        } else if (noShowStatus == VerificationStatus.GUEST_NO_SHOW) {
            verification.markApplicantNoShow();
        } else if (noShowStatus == VerificationStatus.BOTH_NO_SHOW) {
            verification.markBothNoShow();
        }
    }

    private void saveDisputeScenarioChatMessages(Post post, User author, User applicant, String label) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(post.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .postId(post.getId())
                                .roomType(ChatRoomType.ONE_TO_ONE)
                                .build()
                ));

        saveChatMemberIfMissing(chatRoom.getId(), author.getId(), ChatMemberRole.HOST);
        saveChatMemberIfMissing(chatRoom.getId(), applicant.getId(), ChatMemberRole.GUEST);
        saveChatMessageIfMissing(chatRoom.getId(), author.getId(), "%s / 등록자: 약속 장소에서 기다리고 있었습니다.".formatted(label));
        saveChatMessageIfMissing(chatRoom.getId(), applicant.getId(), "%s / 신청자: GPS 인증이 정상 처리되지 않았습니다.".formatted(label));
    }

    private enum DisputeSeedStatus {
        SUBMITTED,
        HOLD
    }

    private void seedAdminAiReviewData(User author, User applicant, User reportTargetUser) {
        Post reportTargetPost = getOrCreateSeedPostByContent(
                reportTargetUser,
                "관리자 AI 신고 분석 테스트용 게시글입니다. 반복 신고와 욕설/비방 판단 확인용입니다.",
                "관리자 AI 신고 테스트 식당",
                LocalDateTime.now().plusHours(3),
                500
        );
        saveReportIfMissing(
                applicant.getId(),
                reportTargetPost.getId(),
                ReportReason.ABUSE,
                "관리자 AI 신고 분석 seed: 게시글 한마디와 채팅에서 비방성 표현이 반복되어 신고합니다."
        );
        saveReportIfMissing(
                author.getId(),
                reportTargetPost.getId(),
                ReportReason.SPAM,
                "관리자 AI 신고 분석 seed: 같은 모집글을 반복적으로 올리는 스팸 의심 사용자입니다."
        );

        Post disputePost = getOrCreateSeedPostByContent(
                author,
                "관리자 AI 이의제기 분석 테스트용 게시글입니다. GPS 오류로 인증에 실패한 상황입니다.",
                "관리자 AI 이의제기 테스트 장소",
                LocalDateTime.now().minusHours(2),
                400
        );
        disputePost.match();

        Match disputeMatch = getOrCreateSeedMatch(disputePost, applicant, 400);
        disputeMatch.dispute();

        MeetVerification disputeVerification = getOrCreateDisputeMeetVerification(disputeMatch.getId());
        saveDisputeIfMissing(
                disputeMatch.getId(),
                applicant.getId(),
                DisputeType.GPS_ERROR,
                "관리자 AI 이의제기 seed: 약속 장소에는 도착했지만 GPS 인증 버튼이 계속 실패했습니다. 채팅으로 도착 사실을 알렸습니다."
        );
        saveAdminAiDisputeChatMessages(disputePost, author, applicant);
    }

    private void seedAdminAiPaymentData(User author, User applicant, User hacker) {
        savePaymentSeedIfMissing(
                author,
                "hankki_admin_ai_paid_author_001",
                ChargePackage.P_10000,
                "card",
                "PAID"
        );
        savePaymentSeedIfMissing(
                applicant,
                "hankki_admin_ai_paid_applicant_001",
                ChargePackage.P_5000,
                "kakaopay",
                "PAID"
        );
        savePaymentSeedIfMissing(
                applicant,
                "hankki_admin_ai_ready_applicant_001",
                ChargePackage.P_3000,
                "card",
                "READY"
        );
        savePaymentSeedIfMissing(
                author,
                "hankki_admin_ai_cancelled_author_001",
                ChargePackage.P_3000,
                "card",
                "CANCELLED"
        );
        savePaymentSeedIfMissing(
                hacker,
                "hankki_admin_ai_failed_hacker_001",
                ChargePackage.P_20000,
                "card",
                "FAILED"
        );
    }

    private void savePaymentSeedIfMissing(
            User user,
            String merchantUid,
            ChargePackage chargePackage,
            String payMethod,
            String status
    ) {
        if (paymentRepository.existsByMerchantUid(merchantUid)) {
            return;
        }

        Payment payment = Payment.builder()
                .userId(user.getId())
                .merchantUid(merchantUid)
                .chargePackage(chargePackage)
                .payMethod(payMethod)
                .build();

        if ("PAID".equals(status)) {
            payment.markPaid();
        } else if ("CANCELLED".equals(status)) {
            payment.markPaid();
            payment.markCancelled("관리자 AI 결제 요약 seed: 결제 취소 테스트 데이터");
        } else if ("FAILED".equals(status)) {
            payment.markFailed("관리자 AI 결제 요약 seed: 결제 실패 테스트 데이터");
        }

        paymentRepository.save(payment);
    }

    private Post getOrCreateSeedPostByContent(
            User author,
            String content,
            String placeName,
            LocalDateTime meetAt,
            int authorDeposit
    ) {
        return postRepository.findAll().stream()
                .filter(post -> content.equals(post.getContent()))
                .findFirst()
                .orElseGet(() -> postRepository.save(
                        Post.builder()
                                .authorId(author.getId())
                                .meetAt(meetAt)
                                .placeName(placeName)
                                .placeLat(DEMO_PLACE_LAT)
                                .placeLng(DEMO_PLACE_LNG)
                                .content(content)
                                .authorDeposit(authorDeposit)
                                .maxApplicants(2)
                                .build()
                ));
    }

    private void saveReportIfMissing(Long reporterId, Long targetPostId, ReportReason reason, String detail) {
        boolean exists = reportRepository.existsByReporterIdAndTargetIdAndStatusIn(
                reporterId,
                targetPostId,
                List.of(ReportStatus.PENDING, ReportStatus.ACCEPTED, ReportStatus.REJECTED)
        );

        if (exists) {
            return;
        }

        reportRepository.save(
                Report.builder()
                        .reporterId(reporterId)
                        .targetId(targetPostId)
                        .reason(reason)
                        .detail(detail)
                        .build()
        );
    }

    private Match getOrCreateSeedMatch(Post post, User applicant, int applicantDeposit) {
        return matchRepository.findAllByPostId(post.getId()).stream()
                .filter(match -> match.getApplicantId().equals(applicant.getId()))
                .findFirst()
                .orElseGet(() -> matchRepository.save(
                        Match.builder()
                                .postId(post.getId())
                                .applicantId(applicant.getId())
                                .applicantDeposit(applicantDeposit)
                                .build()
                ));
    }

    private MeetVerification getOrCreateDisputeMeetVerification(Long matchId) {
        return meetVerificationRepository.findByMatchId(matchId)
                .orElseGet(() -> {
                    MeetVerification verification = MeetVerification.createPending(matchId);
                    verification.verifyAuthorPlace();
                    verification.markApplicantNoShow();
                    verification.markDispute();
                    return meetVerificationRepository.save(verification);
                });
    }

    private void saveDisputeIfMissing(Long matchId, Long submitterId, DisputeType disputeType, String reason) {
        if (disputeRepository.existsByMatchIdAndSubmitterId(matchId, submitterId)) {
            return;
        }

        disputeRepository.save(
                Dispute.builder()
                        .matchId(matchId)
                        .submitterId(submitterId)
                        .disputeType(disputeType)
                        .reason(reason)
                        .evidenceUrl("https://example.com/admin-ai-dispute-gps-evidence.png")
                        .build()
        );
    }

    private void saveAdminAiDisputeChatMessages(Post post, User author, User applicant) {
        ChatRoom chatRoom = chatRoomRepository.findByPostId(post.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .postId(post.getId())
                                .roomType(ChatRoomType.ONE_TO_ONE)
                                .build()
                ));

        saveChatMemberIfMissing(chatRoom.getId(), author.getId(), ChatMemberRole.HOST);
        saveChatMemberIfMissing(chatRoom.getId(), applicant.getId(), ChatMemberRole.GUEST);

        saveChatMessageIfMissing(chatRoom.getId(), applicant.getId(), "관리자 AI seed: 저 장소 앞에 도착했는데 GPS 인증이 실패해요.");
        saveChatMessageIfMissing(chatRoom.getId(), author.getId(), "관리자 AI seed: 저는 먼저 도착해서 기다리고 있습니다.");
        saveChatMessageIfMissing(chatRoom.getId(), applicant.getId(), "관리자 AI seed: 앱 위치 권한을 다시 켰는데도 인증 버튼이 안 됩니다.");
    }

    private void saveChatMemberIfMissing(Long chatRoomId, Long userId, ChatMemberRole role) {
        boolean exists = chatMemberRepository.findAll().stream()
                .anyMatch(member -> member.getChatRoomId().equals(chatRoomId) && member.getUserId().equals(userId));

        if (exists) {
            return;
        }

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatRoomId(chatRoomId)
                        .userId(userId)
                        .role(role)
                        .build()
        );
    }

    private void saveChatMessageIfMissing(Long chatRoomId, Long senderId, String content) {
        boolean exists = chatMessageRepository.findByChatRoomIdOrderByIdAsc(chatRoomId).stream()
                .anyMatch(message -> content.equals(message.getContent()));

        if (exists) {
            return;
        }

        chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoomId(chatRoomId)
                        .senderId(senderId)
                        .content(content)
                        .build()
        );
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
                "google.com",
                University.builder()
                        .universityName("구글대학교")
                        .eDomain("google.com")
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
                "ai-seed-google@google.com",
                "구글AI추천",
                googleUniversity.getId()
        );

        saveTermAgreementIfNotExists(koreaAiSeedUser.getId(), "v1.0");
        saveTermAgreementIfNotExists(naverAiSeedUser.getId(), "v1.0");
        saveTermAgreementIfNotExists(googleAiSeedUser.getId(), "v1.0");
        giveSignupBonusIfNotExists(koreaAiSeedUser, 10000);
        giveSignupBonusIfNotExists(naverAiSeedUser, 10000);
        giveSignupBonusIfNotExists(googleAiSeedUser, 10000);

        seedAiMatchingRecommendationPosts(List.of(koreaAiSeedUser, naverAiSeedUser, googleAiSeedUser));
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

    private void seedAiMatchingRecommendationPosts(List<User> authors) {
        normalizeExistingAiMatchingRecommendationPosts();

        List<AiMatchingSeedPost> seeds = List.of(
                new AiMatchingSeedPost("후문 치킨집", "치킨이랑 감자튀김 시켜서 야식 먹을 분 구해요. 수다 많고 활발한 분위기면 좋아요.", 300, 4),
                new AiMatchingSeedPost("정문 국밥골목", "수업 전에 국밥으로 든든하게 먹고 바로 헤어질 분 찾습니다.", 500, 2),
                new AiMatchingSeedPost("공대 앞 파스타집", "크림파스타 먹으면서 조용하게 저녁 먹을 분 구해요. 말 많이 안 해도 편해요.", 900, 2),
                new AiMatchingSeedPost("학생회관 라멘집", "라멘 한 그릇 빠르게 먹고 다음 수업 가실 분 있나요?", 400, 2),
                new AiMatchingSeedPost("정문 돈까스집", "돈까스 먹으면서 가볍게 이야기할 사람 구해요. 처음 봐도 부담 없는 분위기 좋아요.", 500, 3),
                new AiMatchingSeedPost("후문 쌀국수집", "쌀국수 먹으면서 차분하게 점심 드실 분 구합니다.", 600, 2),
                new AiMatchingSeedPost("학생회관 덮밥집", "덮밥으로 든든하게 점심 먹고 짧게 헤어질 분 찾아요.", 400, 3),
                new AiMatchingSeedPost("중앙광장 분식집", "떡볶이랑 김밥 같이 먹을 분 구해요. 편하게 수다 떨면 좋겠습니다.", 300, 3),
                new AiMatchingSeedPost("후문 마라탕집", "마라탕 같이 담아서 먹을 분 구합니다. 매운 음식 좋아하는 분이면 좋아요.", 700, 3),
                new AiMatchingSeedPost("정문 중국집", "짜장면이나 짬뽕 먹을 사람 구해요. 빠르게 먹고 헤어져도 괜찮아요.", 400, 2),
                new AiMatchingSeedPost("도서관 샌드위치 카페", "샌드위치로 간단하게 브런치 먹을 분 찾아요. 조용한 사람 환영합니다.", 300, 2),
                new AiMatchingSeedPost("기숙사 컵밥집", "컵밥 먹으면서 혼밥 피하고 싶은 분 있으면 같이 먹어요.", 300, 2),
                new AiMatchingSeedPost("정문 김치찌개집", "김치찌개 보글보글 끓여서 든든하게 저녁 먹을 분 구합니다.", 500, 4),
                new AiMatchingSeedPost("후문 초밥집", "초밥 조금씩 나눠 먹을 분 찾아요. 말수 적어도 괜찮습니다.", 800, 2),
                new AiMatchingSeedPost("공학관 편의점", "삼각김밥이랑 컵라면으로 빠르게 밥 먹고 갈 분 구해요.", 200, 2),
                new AiMatchingSeedPost("중앙광장 푸드트럭", "타코야끼랑 음료 들고 가볍게 이야기할 분 찾습니다.", 300, 3),
                new AiMatchingSeedPost("후문 부대찌개집", "부대찌개 같이 끓여 먹을 분 구해요. 든든한 저녁 원합니다.", 600, 4),
                new AiMatchingSeedPost("정문 샐러드집", "샐러드랑 샌드위치로 가볍게 먹을 사람 있나요?", 400, 2),
                new AiMatchingSeedPost("학생회관 한식코너", "백반 조용히 먹고 각자 할 일 하러 갈 분 구합니다.", 400, 2),
                new AiMatchingSeedPost("후문 카레집", "카레 먹으면서 편하게 대화할 분 찾아요. 어색하지 않게 먹어요.", 500, 3),
                new AiMatchingSeedPost("기숙사 치킨포차", "늦은 밤 치킨이랑 콜라 먹을 분 구합니다. 활발하게 떠드는 분위기 좋아요.", 800, 5),
                new AiMatchingSeedPost("정문 칼국수집", "칼국수로 따뜻하게 한 끼 먹을 분 찾습니다. 차분한 분위기 선호해요.", 400, 2),
                new AiMatchingSeedPost("후문 제육덮밥집", "제육덮밥 든든하게 먹고 바로 헤어질 분 구해요.", 400, 2),
                new AiMatchingSeedPost("공대 브런치카페", "파스타나 샌드위치 먹으면서 점심 겸 브런치 하실 분 있나요?", 700, 2),
                new AiMatchingSeedPost("학생회관 김밥집", "김밥이랑 라면으로 간단하게 먹을 분 찾아요. 부담 없이 와주세요.", 300, 3),
                new AiMatchingSeedPost("중앙 짬뽕집", "짬뽕으로 얼큰하게 점심 먹을 사람 구해요. 말 많지 않아도 괜찮아요.", 500, 2),
                new AiMatchingSeedPost("후문 파스타바", "토마토파스타 먹으면서 천천히 이야기할 분 구합니다.", 900, 2),
                new AiMatchingSeedPost("정문 라멘야", "라멘 먹고 카페까지는 안 가고 바로 헤어질 분 찾습니다.", 500, 2),
                new AiMatchingSeedPost("기숙사 국밥집", "국밥 한 그릇 먹고 힘내실 분 구해요. 든든하게 먹을 사람 환영합니다.", 600, 3),
                new AiMatchingSeedPost("후문 돈까스골목", "치즈돈까스 먹을 분 구해요. 재밌게 이야기하면서 먹으면 좋겠습니다.", 600, 3),
                new AiMatchingSeedPost("정문 베트남식당", "쌀국수랑 볶음밥 같이 먹을 분 찾아요. 조용하게 먹어도 좋아요.", 600, 2),
                new AiMatchingSeedPost("학생회관 오므라이스집", "오므라이스로 가볍게 점심 먹을 분 구합니다. 수업 사이 빠르게 먹어요.", 400, 2),
                new AiMatchingSeedPost("후문 야식분식", "떡볶이랑 튀김으로 야식 먹을 사람 있나요? 수다 많은 분 좋아요.", 500, 4),
                new AiMatchingSeedPost("공대 앞 중식당", "짜장면 탕수육 같이 시킬 분 구합니다. 여럿이 나눠 먹어요.", 800, 5),
                new AiMatchingSeedPost("도서관 앞 죽집", "죽이나 가벼운 한식 먹을 분 찾습니다. 조용한 식사 원해요.", 300, 2),
                new AiMatchingSeedPost("정문 햄버거집", "햄버거 세트 빠르게 먹고 헤어질 사람 구해요.", 400, 2),
                new AiMatchingSeedPost("후문 브리또집", "브리또나 타코 먹으면서 재밌게 이야기할 분 찾아요.", 500, 3),
                new AiMatchingSeedPost("학생회관 찌개코너", "된장찌개로 든든하게 먹을 분 구해요. 혼자 먹기 싫어서 올립니다.", 400, 3),
                new AiMatchingSeedPost("정문 우동집", "우동 한 그릇 조용하게 먹고 갈 분 있나요?", 300, 2),
                new AiMatchingSeedPost("기숙사 샌드위치점", "샌드위치랑 커피로 늦은 아침 먹을 분 구합니다. 편한 사람 좋아요.", 300, 2)
        );

        LocalDateTime base = LocalDateTime.now().plusMinutes(30);

        for (User author : authors) {
            for (int i = 0; i < 150; i++) {
                AiMatchingSeedPost seed = seeds.get(i % seeds.size());
                int cycle = i / seeds.size();
                int sequence = i + 1;
                LocalDateTime meetAt = base
                        .plusDays(cycle / 2)
                        .plusMinutes((long) sequence * 13);
                int deposit = seed.deposit() + (cycle % 3) * 100;
                int maxApplicants = Math.min(5, Math.max(2, seed.maxApplicants() + (cycle % 2)));
                String content = "[AI추천150-%s-%03d] %s 같이 먹을 분 구해요. %s %s 식사해요."
                        .formatted(
                                author.getEmail(),
                                sequence,
                                seed.menu(),
                                seed.atmosphere(),
                                seed.timeKeyword()
                        );

                if (existsPostByContent(content)) {
                    continue;
                }

                postRepository.save(
                        Post.builder()
                                .authorId(author.getId())
                                .meetAt(meetAt)
                                .placeName(seed.placeName())
                                .placeLat(DEMO_PLACE_LAT)
                                .placeLng(DEMO_PLACE_LNG)
                                .content(seed.content())
                                .authorDeposit(deposit)
                                .maxApplicants(maxApplicants)
                                .build()
                );
            }
        }
    }

    private boolean normalizeExistingAiMatchingRecommendationPosts() {
        List<Post> aiSeedPosts = postRepository.findAll().stream()
                .filter(post -> post.getContent() != null && post.getContent().startsWith("[AI추천120-"))
                .toList();

        for (Post post : aiSeedPosts) {
            String normalizedContent = post.getContent()
                    .replaceFirst("^\\[AI추천120-\\d{3}]\\s*", "")
                    .replaceFirst("\\s*책임비는\\s*\\d+P입니다\\.$", "");
            String normalizedPlaceName = post.getPlaceName().replaceFirst("\\s+\\d{2}$", "");

            post.update(
                    null,
                    normalizedPlaceName,
                    null,
                    null,
                    normalizedContent,
                    null
            );
        }

        return !aiSeedPosts.isEmpty();
    }

    private record AiMatchingSeedPost(
            String placeName,
            String content,
            int deposit,
            int maxApplicants
    ) {
    }

    // local seed 게시글도 매칭 AI가 추천할 수 있도록 OPEN + 미래 약속 게시글을 벡터 인덱스에 등록합니다.
    // 이미 seed 데이터가 존재하는 재시작 상황에서도 인덱스가 비어 있을 수 있어 매번 이벤트를 다시 발행합니다.
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

    /**
     * 같은 이메일 도메인의 대학교가 이미 있으면 재사용하고, 없을 때만 새로 저장합니다.
     */
    private University getOrCreateUniversity(String eDomain, University university) {
        return universityRepository.findByeDomainAndIsActiveTrue(eDomain)
                .orElseGet(() -> universityRepository.save(university));
    }

    /**
     * 같은 이메일의 유저가 이미 있으면 재사용하고, 없을 때만 새로 저장합니다.
     */
    private User getOrCreateUser(String email, User user) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(user));
    }

    /**
     * 같은 유저가 같은 약관 버전에 이미 동의했다면 중복 저장하지 않습니다.
     */
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

    /**
     * 회원가입 보너스 거래가 이미 있으면 포인트를 다시 지급하지 않습니다.
     */
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
                        .description("회원가입 보너스 지급")
                        .build()
        );
    }

    /**
     * 매칭별 만남 인증 row는 하나만 필요하므로, 이미 있으면 새로 만들지 않습니다.
     */
    private void saveMeetVerificationIfNotExists(Long matchId) {
        if (meetVerificationRepository.findByMatchId(matchId).isPresent()) {
            return;
        }

        meetVerificationRepository.save(MeetVerification.createPending(matchId));
    }

    /**
     * 채팅방 참여자는 chatRoomId + userId 조합으로 중복되면 안 됩니다.
     */
    private void saveChatMemberIfNotExists(Long chatRoomId, Long userId, ChatMemberRole role) {
        if (chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            return;
        }

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatRoomId(chatRoomId)
                        .userId(userId)
                        .role(role)
                        .build()
        );
    }

    /**
     * 유저 위치는 matchId + userId 조합으로 하나만 유지합니다.
     */
    private void saveUserLocationIfNotExists(Long matchId, Long userId) {
        if (userLocationRepository.findByMatchIdAndUserId(matchId, userId).isPresent()) {
            return;
        }

        userLocationRepository.save(
                UserLocation.builder()
                        .matchId(matchId)
                        .userId(userId)
                        .latitude(DEMO_PLACE_LAT)
                        .longitude(DEMO_PLACE_LNG)
                        .build()
        );
    }
}
