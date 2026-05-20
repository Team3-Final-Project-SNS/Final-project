package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class PostCommandServiceImpl implements PostCommandService {

    private final PostRepository postRepository;
    private final PostQueryService postQueryService;

    // ⚠️ 아래 두 의존성은 다른 도메인 담당자가 구현 예정
    // 시그니처만 합의된 상태로, "존재한다고 가정"하고 호출
    //   - UserCommandService: 포인트 차감
    //   - UserQueryService: 작성자 닉네임 조회
    // TODO: User 도메인 PR 머지 후 실제 import 경로 맞추기
    // private final UserCommandService userCommandService;
    // private final UserQueryService userQueryService;

    @Override
    public CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request) {

        // ===== 1단계: 비즈니스 규칙 검증 =====
        // DTO에서 @Future로 1차 검증했지만, 동시성/시계 오차 등을 고려해 Service에서 다시 확인
        // 명세서 POST_101: "만남 희망 시간은 현재 이후여야 합니다."
        if (request.getMeetAt().isBefore(LocalDateTime.now())) {
            throw new PostException(ErrorCode.POST_INVALID_MEET_AT);
        }

        // 책임비 100P 단위 검증
        // 200, 300, 400, 500... OK / 250, 333 NG
        // DTO에서 @Min(200)으로 최소값은 막았지만, 100 단위는 비즈니스 규칙이므로 여기서 검증
        // Post 엔티티의 상수(DEPOSIT_UNIT = 100)를 직접 참조해 매직넘버 제거
        if (request.getAuthorDeposit() % Post.DEPOSIT_UNIT != 0) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
        }

        // ===== 2단계: 포인트 차감 (다른 도메인 호출 — 예치) =====
        // 잔액 부족 시 UserCommandService가 PointException(POINT_001) 던짐
        // → 같은 트랜잭션 안이므로 이 시점에 예외가 나면 아래 save도 실행 안 됨
        //
        // ✅ 실제 호출은 User 도메인 머지 후 활성화:
        // userCommandService.deductPoint(authorId, request.getAuthorDeposit());
        //
        // 호출 시 User 담당자에게 함께 요청할 사항:
        //   1) PointTransaction 기록 (type=DEPOSIT, balance_after, description="게시글 작성 예치")
        //   2) 같은 트랜잭션 내에서 처리될 것 (이 메서드의 트랜잭션에 참여)

        // ===== 3단계: Post 엔티티 생성 =====
        // 엔티티의 @Builder로 생성 — status는 엔티티 생성자에서 OPEN으로 자동 설정
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(request.getMeetAt())
                .placeName(request.getPlaceName())
                .placeLat(request.getPlaceLat())
                .placeLng(request.getPlaceLng())
                .content(request.getContent()) // null이면 null로 저장 (선택 필드)
                .authorDeposit(request.getAuthorDeposit())
                .build();

        // ===== 4단계: 저장 =====
        // save 호출 시 INSERT 쿼리 발생 → 반환된 엔티티에 DB가 부여한 id 포함
        Post savedPost = postRepository.save(post);

        // ===== 5단계: Response 변환 =====
        // authorNickname은 User 도메인에서 가져와야 함
        // TODO: User 도메인 머지 후 실제 조회로 교체
        // String authorNickname = userQueryService.getUserById(authorId).getNickname();
        String authorNickname = "임시닉네임"; // 임시값

        return CreatePostResponseDto.from(savedPost, authorNickname);
    }

    @Override
    public UpdatePostResponseDto updatePost(Long postId, Long userId, UpdatePostRequestDto request) {

        // 1. 게시글 조회
        Post post = postQueryService.getPostById(postId);

        // 2. 작성자 본인 검증
        // 엔티티의 isAuthor() 헬퍼 활용 → 비교 로직을 엔티티가 책임짐
        if (!post.isAuthor(userId)) {
            throw new PostException(ErrorCode.POST_NOT_AUTHOR);
        }

        // 3. 상태 검증 — OPEN만 수정 가능
        if (!post.isOpen()) {
            throw new PostException(ErrorCode.POST_NOT_OPEN);
        }

        // 4. authorDeposit 검증 + 차액 처리
        Integer newDeposit = request.getAuthorDeposit();

        if (newDeposit != null) {
            // Post.DEPOSIT_UNIT(=100) 상수 참조로 매직넘버 제거
            if (newDeposit % Post.DEPOSIT_UNIT != 0) {
                throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
            }

            // 차액 계산 — 양수면 추가 차감 필요, 음수면 환불 필요
            int oldDeposit = post.getAuthorDeposit();
            int diff = newDeposit - oldDeposit;

            if (diff > 0) {
                // 증액: diff만큼 추가 차감 — 잔액 부족 시 User 도메인이 POINT_001 던짐
                // TODO: User 도메인 머지 후 활성화
                // userCommandService.deductPoint(userId, diff);
                // ※ PointTransaction status=EDIT_DEPOSIT, description="게시글 수정 추가 예치"

            } else if (diff < 0) {
                // 감액: |diff|만큼 환불
                // TODO: User 도메인 머지 후 활성화
                // userCommandService.refundPoint(userId, Math.abs(diff));
                // ※ PointTransaction status=EDIT_DEPOSIT, description="게시글 수정 차액 환불"
            }
            // diff == 0이면 아무것도 안 함 (같은 금액으로 "수정")
        }

        // 5. 엔티티 update() 호출
        post.update(
                request.getMeetAt(),
                request.getPlaceName(),
                request.getPlaceLat(),
                request.getPlaceLng(),
                request.getContent(),
                request.getAuthorDeposit()
        );

        // 6. 응답 DTO 변환
        return UpdatePostResponseDto.from(post);
    }

    @Override
    public void completePost(Long postId) {
        // 1. Post 엔티티 조회 — PostQueryService에 위임
        //    (같은 도메인의 Query Service를 재사용 → 단건 조회 + NotFound 처리 일원화)
        Post post = postQueryService.getPostById(postId);

        // 2. 도메인 메서드 호출 — 상태 전이 규칙은 엔티티가 책임
        //    @Transactional 안에서 엔티티 필드 변경 → 더티체킹으로 자동 UPDATE
        post.complete();
    }
}
