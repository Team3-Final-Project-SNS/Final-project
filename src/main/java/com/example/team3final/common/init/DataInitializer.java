package com.example.team3final.common.init;

import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.entity.ChatMember;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.chat.repository.ChatMessageRepository;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.location.entity.UserLocation;
import com.example.team3final.domain.location.repository.UserLocationRepository;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.pointTransaction.entity.PointTransaction;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.repository.PointTransactionRepository;
import com.example.team3final.domain.post.entity.Post;
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
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Profile("local")
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // ===================================================
        // 1. 대학교 생성
        // ===================================================
        University university = universityRepository.save(
                University.builder()
                        .universityName("한국대학교")
                        .eDomain("korea.ac.kr")
                        .isActive(true)
                        .build()
        );

        University university1 = universityRepository.save(
                University.builder()
                        .universityName("네이버대학교")
                        .eDomain("naver.com")
                        .isActive(true)
                        .build()
        );

        // ===================================================
        // 2. 유저 생성 (초기 생성 시 point는 자동으로 0 세팅됨)
        // ===================================================
        User author = userRepository.save(
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

        User applicant = userRepository.save(
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

        User hacker = userRepository.save(
                User.builder()
                        .email("hacker@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!"))
                        .name("나해커")
                        .nickname("정당한참여자아님")
                        .universityId(university.getId())
                        .major("법학과")
                        .studentNumber("22")
                        .birthDate(LocalDate.of(2002, 1, 1))
                        .gender(Gender.MALE)
                        .build()
        );

        // ===================================================
        // 3. 약관 동의 이력 생성
        // ===================================================
        termAgreementRepository.save(TermAgreement.builder().userId(author.getId()).termVersion("v1.0").build());
        termAgreementRepository.save(TermAgreement.builder().userId(applicant.getId()).termVersion("v1.0").build());
        termAgreementRepository.save(TermAgreement.builder().userId(hacker.getId()).termVersion("v1.0").build());

        // ===================================================
        // 4. 포인트 가입 보너스 지급 (기본 10,000포인트 충전)
        // ===================================================
        int authorBonus = 10000;
        int applicantBonus = 10000;
        int hackerBonus = 10000;

        // 엔티티 내부의 addPoint 메서드를 사용하여 포인트 반영
        author.addPoint(authorBonus);
        applicant.addPoint(applicantBonus);
        hacker.addPoint(hackerBonus);

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .amount(authorBonus)
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(authorBonus)
                        .description("회원가입 보너스 지급")
                        .build()
        );

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .amount(applicantBonus)
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(applicantBonus)
                        .description("회원가입 보너스 지급")
                        .build()
        );

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(hacker.getId())
                        .amount(hackerBonus)
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(hackerBonus)
                        .description("회원가입 보너스 지급")
                        .build()
        );

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
        author.deductPoint(300);

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(activeMatch.getId())
                        .amount(-300)
                        .transactionType(PointTransactionType.DEPOSIT)
                        .balanceAfter(authorBonus - 300)
                        .description("게시글 작성 책임비 예치")
                        .build()
        );

        meetVerificationRepository.save(MeetVerification.createPending(activeMatch.getId()));

        userLocationRepository.save(UserLocation.builder().matchId(activeMatch.getId()).userId(author.getId()).latitude(DEMO_PLACE_LAT).longitude(DEMO_PLACE_LNG).build());
        userLocationRepository.save(UserLocation.builder().matchId(activeMatch.getId()).userId(applicant.getId()).latitude(DEMO_PLACE_LAT).longitude(DEMO_PLACE_LNG).build());

        ChatRoom activeChatRoom = chatRoomRepository.save(ChatRoom.builder().matchId(activeMatch.getId()).build());

        chatMemberRepository.save(ChatMember.builder().chatRoomId(activeChatRoom.getId()).userId(author.getId()).role(ChatMemberRole.HOST).build());
        chatMemberRepository.save(ChatMember.builder().chatRoomId(activeChatRoom.getId()).userId(applicant.getId()).role(ChatMemberRole.GUEST).build());

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

        ChatRoom completedChatRoom = chatRoomRepository.save(ChatRoom.builder().matchId(completedMatch.getId()).build());

        chatMemberRepository.save(ChatMember.builder().chatRoomId(completedChatRoom.getId()).userId(author.getId()).role(ChatMemberRole.HOST).build());
        chatMemberRepository.save(ChatMember.builder().chatRoomId(completedChatRoom.getId()).userId(applicant.getId()).role(ChatMemberRole.GUEST).build());

        chatMessageRepository.save(ChatMessage.builder().chatRoomId(completedChatRoom.getId()).senderId(author.getId()).content("예전 완료된 대화내용입니다.").build());

        // 원래는 완료되면서 차감되었다가 환급(REFUND)된 케이스이므로 결과적으로 잔액 변동 없음
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(completedMatch.getId())
                        .amount(300)
                        .transactionType(PointTransactionType.REFUND)
                        .balanceAfter(authorBonus)
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
        applicant.deductPoint(500);

        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .matchId(cancelledMatch.getId())
                        .amount(-500)
                        .transactionType(PointTransactionType.PENALTY)
                        .balanceAfter(applicantBonus - 500)
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
    }
}