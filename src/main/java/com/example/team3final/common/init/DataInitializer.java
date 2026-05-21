package com.example.team3final.common.init;

import com.example.team3final.domain.chat.entity.ChatMessage;
import com.example.team3final.domain.chat.entity.ChatRoom;
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

        // ===================================================
        // 2. 유저 생성 (정상 참여자 2명 및 제3자 테스트용 유저 1명)
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

        // CHAT-E-001 (제3자 권한 예외) 테스트용 유저
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
        // 4. 포인트 가입 보너스 내역 (기본 10,000포인트 + 랜덤 포인트 추가)
        // ===================================================

        // 10,000원 기본 보너스에 0원 ~ 10,000원 사이의 100원 단위 랜덤 포인트 추가
        int authorBonus = 10000;
        int applicantBonus = 10000;
        int hackerBonus = 10000;

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
        // 대상 테스트: CHAT-S-001~003, VERI-S-001~005, POINT-S-002~003
        // ===================================================

        // 만남 일시: 현재 시점 기준 +10분 후 (VERI-S-001의 -15분 ~ +1시간 조건 충족)
        Post activePost = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusMinutes(10))
                        .placeName("정문 편의점 앞")
                        .placeLat(new BigDecimal("37.3745300"))
                        .placeLng(new BigDecimal("126.6322100"))
                        .content("활성화된 채팅 및 인증 테스트용 방입니다.")
                        .authorDeposit(300)
                        .build()
        );
        activePost.match(); // 상태 MATCHED 전환

        Match activeMatch = matchRepository.save(
                Match.builder()
                        .postId(activePost.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)
                        .build()
        );

        // 포인트 예치금 트랜잭션 적재
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

        // 만남 인증 대기(PENDING) 생성
        meetVerificationRepository.save(MeetVerification.createPending(activeMatch.getId()));

        // 유저 위치 설정 (장소 반경 60m 이내 - 약 30~40m 거리)
        userLocationRepository.save(UserLocation.builder().matchId(activeMatch.getId()).userId(author.getId()).latitude(new BigDecimal("37.3745100")).longitude(new BigDecimal("126.6321800")).build());
        userLocationRepository.save(UserLocation.builder().matchId(activeMatch.getId()).userId(applicant.getId()).latitude(new BigDecimal("37.3744900")).longitude(new BigDecimal("126.6322400")).build());

        // 활성 채팅방 및 메시지 설정
        ChatRoom activeChatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                        .matchId(activeMatch.getId())
                        .authorId(author.getId())
                        .applicantId(applicant.getId())
                        .build()
        );

        ChatMessage msg1 = chatMessageRepository.save(ChatMessage.builder().chatRoom(activeChatRoom).senderId(author.getId()).content("안녕하세요!").build());
        msg1.markAsRead();
        ChatMessage msg2 = chatMessageRepository.save(ChatMessage.builder().chatRoom(activeChatRoom).senderId(applicant.getId()).content("네 반갑습니다.").build());
        msg2.markAsRead();
        chatMessageRepository.save(ChatMessage.builder().chatRoom(activeChatRoom).senderId(author.getId()).content("안 읽은 메시지 테스트용").build()); // unreadCount용 고의 누락

        // ===================================================
        // CASE B. 비활성화/종료된 매칭 (COMPLETED)
        // 대상 테스트: CHAT-S-004 (읽기전용 조회), CHAT-E-002 (메시지 전송 차단)
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

        ChatRoom completedChatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                        .matchId(completedMatch.getId())
                        .authorId(author.getId())
                        .applicantId(applicant.getId())
                        .build()
        );
        chatMessageRepository.save(ChatMessage.builder().chatRoom(completedChatRoom).senderId(author.getId()).content("예전 완료된 대화내용입니다.").build());

        // POINT-S-004 검증용 환급(REFUND) 트랜잭션 추가
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(completedMatch.getId())
                        .amount(300)
                        .transactionType(PointTransactionType.REFUND)
                        .balanceAfter(authorBonus) // 예치금이 그대로 돌아왔으므로 초기 보너스 값과 동일
                        .description("만남 인증 완료로 인한 책임비 환급")
                        .build()
        );

        // ===================================================
        // CASE C. 취소된 매칭 (CANCELLED)
        // 대상 테스트: VERI-E-004 (CANCELLED 후 재입장 시도 bad request 검증)
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

        // POINT-S-004 검증용 벌점(PENALTY) 트랜잭션 추가
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .matchId(cancelledMatch.getId())
                        .amount(-500)
                        .transactionType(PointTransactionType.PENALTY)
                        .balanceAfter(applicantBonus - 500) // 가입 보너스에서 패널티 차감
                        .description("당일 취소로 인한 패널티 차감")
                        .build()
        );
    }
}