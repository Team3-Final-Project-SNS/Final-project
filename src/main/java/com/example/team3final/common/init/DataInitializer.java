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

/**
 * 애플리케이션 시작 시 테스트용 초기 데이터를 자동으로 삽입하는 클래스
 * - @Profile("local"): application-local.yml 또는 --spring.profiles.active=local 일 때만 실행
 * → 운영(prod) 환경에서 실수로 데이터가 들어가는 사고를 방지
 * - ApplicationRunner: 스프링 컨텍스트 로딩이 완전히 끝난 뒤 run() 메서드를 딱 한 번 실행
 */
@Profile("local")
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    // ===== Repository 주입 =====
    // 각 도메인의 저장소를 주입받아 직접 DB에 저장
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

    // 비밀번호를 BCrypt로 암호화하기 위해 주입 (SecurityConfig에서 @Bean 등록된 것)
    private final PasswordEncoder passwordEncoder;

    /**
     * 스프링 부팅 완료 후 딱 한 번 실행되는 메서드
     *
     * @Transactional: 아래 모든 save 작업이 하나의 트랜잭션으로 묶임
     * 중간에 예외 발생 시 전체 롤백 → DB 부분 삽입 방지
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // ===================================================
        // 1. 대학교 생성
        // ===================================================
        // 테스트용 학교 2개 — 유저 생성 시 universityId로 참조
        University university = universityRepository.save(
                University.builder()
                        .universityName("한국대학교")          // 학교명
                        .eDomain("korea.ac.kr")               // 이메일 도메인 (unique 제약)
                        .isActive(true)                        // 활성 상태
                        .build()
        );

        University university1 = universityRepository.save(
                University.builder()
                        .universityName("광운대학교")          // 학교명
                        .eDomain("iit.kw.ac.kr")               // 이메일 도메인 (unique 제약)
                        .isActive(true)                        // 활성 상태
                        .build()
        );

        // ===================================================
        // 2. 유저 생성 — 등록자(author), 신청자(applicant)
        // ===================================================
        // 등록자: 게시글을 작성하는 유저
        User author = userRepository.save(
                User.builder()
                        .email("author@korea.ac.kr")
                        .password(passwordEncoder.encode("password123!")) // BCrypt 암호화
                        .name("김등록")
                        .nickname("밥먹자")
                        .universityId(university.getId())
                        .major("컴퓨터공학과")
                        .studentNumber("24")                   // 입학연도 뒤 2자리
                        .birthDate(LocalDate.of(2004, 3, 15))
                        .gender(Gender.MALE)
                        .build()
        );

        // 신청자: 게시글에 매칭을 신청하는 유저
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

        // ===================================================
        // 3. 약관 동의 이력 생성
        // ===================================================
        // 두 유저 모두 최신 약관에 동의한 상태로 초기화
        termAgreementRepository.save(
                TermAgreement.builder()
                        .userId(author.getId())
                        .termVersion("v1.0")
                        .build()
        );
        termAgreementRepository.save(
                TermAgreement.builder()
                        .userId(applicant.getId())
                        .termVersion("v1.0")
                        .build()
        );

        // ===================================================
        // 4. 포인트 거래 내역 — 가입 보너스
        // ===================================================
        // 실제 서비스에서는 UserCommandService에서 처리하지만,
        // InitData에서는 직접 PointTransaction만 저장 (User.point 필드는 0 그대로 유지)
        // → 테스트 시 포인트 거래 내역 조회 API 확인용

        // 등록자 가입 보너스 10,000P
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(null)                         // 가입 보너스는 매칭 무관 → null
                        .amount(10_000)                        // 지급량
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(10_000)                  // 거래 후 잔액
                        .description("회원가입 보너스 지급")
                        .build()
        );

        // 신청자 가입 보너스 10,000P
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .matchId(null)
                        .amount(10_000)
                        .transactionType(PointTransactionType.JOIN_BONUS)
                        .balanceAfter(10_000)
                        .description("회원가입 보너스 지급")
                        .build()
        );

        // ===================================================
        // 5. 게시글 생성
        // ===================================================
        // 인천대학교 정문 근처 좌표 사용 (테스트 GPS 인증에 활용 가능)
        Post post = postRepository.save(
                Post.builder()
                        .authorId(author.getId())
                        .meetAt(LocalDateTime.now().plusMinutes(10))  // 1시간 뒤 만남
                        .placeName("정문 편의점 앞")
                        .placeLat(new BigDecimal("37.3745300"))     // 위도 (소수점 7자리)
                        .placeLng(new BigDecimal("126.6322100"))    // 경도
                        .content("같이 밥 먹어요! 편의점 도시락도 환영합니다 :)")
                        .authorDeposit(300)                        // 책임비 300P (100 단위)
                        .build()
        );

        // 게시글 상태를 MATCHED로 전환 (매칭 확정 상태 시뮬레이션)
        // Post.match()는 status → MATCHED 로 바꿔주는 엔티티 비즈니스 메서드
        post.match();

        // ===================================================
        // 6. 매칭 생성
        // ===================================================
        // Match 엔티티: 게시글 ID + 신청자 ID + 신청자 예치 포인트
        Match match = matchRepository.save(
                Match.builder()
                        .postId(post.getId())
                        .applicantId(applicant.getId())
                        .applicantDeposit(300)                     // 신청자도 동일 책임비
                        .build()
        );

        // ===================================================
        // 7. 포인트 거래 내역 — 예치 (매칭 확정 시)
        // ===================================================
        // PointTransaction.match_id unique 제약 때문에
        // 등록자/신청자 각각 별도 레코드로 저장

        // 등록자 책임비 예치 -300P
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(author.getId())
                        .matchId(match.getId())
                        .amount(-300)                              // 차감이므로 음수
                        .transactionType(PointTransactionType.DEPOSIT)
                        .balanceAfter(9_700)                       // 10000 - 300
                        .description("게시글 작성 책임비 예치")
                        .build()
        );

        // 신청자 책임비 예치 -300P
        // ⚠️ match_id unique 제약으로 같은 matchId를 두 레코드에 쓸 수 없음
        // → 실제 서비스 설계에서도 이 제약은 수정이 필요할 수 있음 (현재 InitData에서는 null 처리)
        pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(applicant.getId())
                        .matchId(null)                             // unique 제약 회피 (임시)
                        .amount(-300)
                        .transactionType(PointTransactionType.DEPOSIT)
                        .balanceAfter(9_700)
                        .description("매칭 신청 책임비 예치")
                        .build()
        );

        // ===================================================
        // 8. 만남 인증 레코드 생성
        // ===================================================
        // MeetVerification.createPending(): status=PENDING, isMeetVerified=false 로 초기화
        meetVerificationRepository.save(
                MeetVerification.createPending(match.getId())
        );

        // 교체 — QR 테스트용으로 VERIFIED 상태로 초기화
//        MeetVerification meetVerification = MeetVerification.createPending(match.getId());
//        meetVerification.verifyAuthorPlace();    // 등록자 GPS 인증 완료
//        meetVerification.verifyApplicantPlace(); // 신청자 GPS 인증 완료
//        // 이 시점에서 status = VERIFIED 로 자동 전환
//        meetVerificationRepository.save(meetVerification);

        // ===================================================
        // 9. 유저 위치 레코드 생성
        // ===================================================
        // GPS 장소 인증 화면 테스트용 — 두 유저 모두 약속 장소 근처에 위치
        // (match_id + user_id unique 제약 있음)

        // 등록자 위치 — 약속 장소 기준 약 30m 이내
        userLocationRepository.save(
                UserLocation.builder()
                        .matchId(match.getId())
                        .userId(author.getId())
                        .latitude(new BigDecimal("37.3745100"))
                        .longitude(new BigDecimal("126.6321800"))
                        .build()
        );

        // 신청자 위치 — 약속 장소 기준 약 40m 이내
        userLocationRepository.save(
                UserLocation.builder()
                        .matchId(match.getId())
                        .userId(applicant.getId())
                        .latitude(new BigDecimal("37.3744900"))
                        .longitude(new BigDecimal("126.6322400"))
                        .build()
        );

        // ===================================================
        // 10. 채팅방 생성
        // ===================================================
        // 요구사항: 두 유저가 참여한 활성 채팅방 1개
        // ChatRoom은 matchId로 채팅방을 식별 (unique 제약)
        ChatRoom chatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                        .matchId(match.getId())
                        .authorId(author.getId())       // TODO: Match 도메인 완성 후 제거 예정
                        .applicantId(applicant.getId()) // TODO: Match 도메인 완성 후 제거 예정
                        .build()
        );

        // ===================================================
        // 11. 채팅 메시지 생성 (4개, 읽음/안읽음 혼합)
        // ===================================================
        // 요구사항: 3~5개, 읽음/안읽음 섞어서

        // 메시지 1 — 등록자 발송, 읽음 처리됨
        ChatMessage msg1 = chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .senderId(author.getId())
                        .content("안녕하세요! 1시간 뒤 정문에서 만나요~")
                        .build()
        );
        msg1.markAsRead(); // 신청자가 읽음

        // 메시지 2 — 신청자 발송, 읽음 처리됨
        ChatMessage msg2 = chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .senderId(applicant.getId())
                        .content("네! 알겠습니다. 편의점 앞에서 만나요")
                        .build()
        );
        msg2.markAsRead(); // 등록자가 읽음

        // 메시지 3 — 등록자 발송, 읽음 처리됨
        ChatMessage msg3 = chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .senderId(author.getId())
                        .content("혹시 먹고 싶은 거 있으세요?")
                        .build()
        );
        msg3.markAsRead();

        // 메시지 4 — 신청자 발송, 아직 안 읽음 (isRead = false 기본값)
        // 등록자가 아직 확인 안 한 상태 → 읽음 처리 없이 저장만
        chatMessageRepository.save(
                ChatMessage.builder()
                        .chatRoom(chatRoom)
                        .senderId(applicant.getId())
                        .content("저는 뭐든 괜찮아요! 😊")
                        .build()
                // markAsRead() 호출 안 함 → isRead = false 유지
        );
    }
}