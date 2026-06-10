package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.*;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostServiceImpl implements PostService{

    private final PostRepository postRepository;
    private final UserService userService;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;
    private final ReviewAvoidanceService reviewAvoidanceService;
    private final RedisPostService redisPostService;


    @Override
    @Transactional
    public CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request) {

        // 만남 시간 검증
        if (request.getMeetAt().isBefore(LocalDateTime.now())) {
            throw new PostException(ErrorCode.POST_INVALID_MEET_AT);
        }

        // 책임비 검증 (1) 최소 200P 이상인지
        if (request.getAuthorDeposit() < Post.MIN_AUTHOR_DEPOSIT) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
        }

        // 책임비 검증 (2) 100P 단위인지 — BUG-07 수정
        // % 는 나머지 연산자 → 100으로 나눴을 때 나머지가 0이 아니면 단위 위반
        // ex) 150 % 100 = 50 → 예외 / 300 % 100 = 0 → 통과
        if (request.getAuthorDeposit() % 100 != 0) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT_UNIT);
        }

        // 2. 포인트 차감
        userPointService.deductPoint(authorId, request.getAuthorDeposit(), null);

        // 3. Post 엔티티 생성
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(request.getMeetAt())
                .placeName(request.getPlaceName())
                .placeLat(request.getPlaceLat())
                .placeLng(request.getPlaceLng())
                .content(request.getContent())
                .authorDeposit(request.getAuthorDeposit())
                .maxApplicants(request.getMaxApplicants()) // 최대 참여 인원 추가
                .build();

        // 4. 저장
        Post savedPost = postRepository.save(post);

        log.info("[Post] 게시글 생성 완료 - postId: {}, authorId: {}, status: {}, meetAt: {}",
                savedPost.getId(), authorId, savedPost.getStatus(), savedPost.getMeetAt());

        // 5. Response
        // userService.getUserInfo(authorId) → UserInfoDto 반환, 거기서 nickname() 추출
        String authorNickname = userService.getUserInfo(authorId).nickname();

        return CreatePostResponseDto.from(savedPost, authorNickname);
    }

    @Override
    @Transactional
    public UpdatePostResponseDto updatePost(Long postId, Long userId, UpdatePostRequestDto request) {

        // 1. 게시글 조회
        Post post = getPostById(postId);

        // 2. 작성자 본인 검증
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
            // 수정 시에도 동일한 100P 단위 검증 — BUG-07 수정
            if (newDeposit % 100 != 0) {
                throw new PostException(ErrorCode.POST_INVALID_DEPOSIT_UNIT);
            }

            // 차액 계산 — 양수면 추가 차감, 음수면 환불
            int oldDeposit = post.getAuthorDeposit();
            int diff = newDeposit - oldDeposit;

            if (diff > 0) {
                // 증액: diff만큼 추가 차감
                 userPointService.deductEditDeposit(userId, diff);
            } else if (diff < 0) {
                // 감액: |diff|만큼 환불
                 userPointService.refundEditDeposit(userId, Math.abs(diff));
            }
            // diff == 0이면 아무것도 안 함
        }

        // 5. 엔티티 update() 호출 — 상태/필드 변경은 도메인 메서드가 책임
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
    @Transactional
    public void completePost(Long postId) {
        // 1. Post 조회
        Post post = getPostById(postId);

        // 이미 COMPLETED면 중복 처리 방지 (그룹 매칭에서 completePost 중복 호출 방어)
        if (post.getStatus() == PostStatus.COMPLETED) {
            return;
        }

        // 2. 도메인 메서드 호출 — 상태 전이 규칙은 엔티티가 책임
        post.complete();

        log.info("[Post] 게시글 완료 처리 - postId: {}, status: {}",
                postId, post.getStatus());

    }

    @Override
    @Transactional
    public DeletePostResponseDto deletePost(Long postId, Long userId) {

        // 1. 게시글 조회
        Post post = getPostById(postId);

        // 2. 작성자 본인 검증
        if (!post.isAuthor(userId)) {
            throw new PostException(ErrorCode.POST_NOT_AUTHOR);
        }

        // 3. 상태 검증 — OPEN만 삭제 가능
        if (!post.isOpen()) {
            throw new PostException(ErrorCode.POST_NOT_OPEN);
        }

        // 4. 환불 금액 추출
        int refundedPoint = post.getAuthorDeposit();

        // 5. 포인트 전액 환불
        userPointService.refundPoint(userId, refundedPoint, null);

        // 6. 게시글 소프트 삭제
        post.delete();

        log.info("[Post] 게시글 삭제 처리 - postId: {}, authorId: {}, refundedPoint: {}",
                postId, userId, refundedPoint);

        return DeletePostResponseDto.of(postId, refundedPoint);
    }

    @Override
    public PageResponseDto<GetPostsItemResponseDto> getPosts(
            Long currentUserId,
            PostStatus status,
            Pageable pageable
    ) {
        // 0. 페이지 크기 검증 — 최대 50 초과 시 예외 (명세서 4.2: size 최대 50)
        // 과도하게 큰 size 요청으로 인한 DB 부하/메모리 폭증을 막는 방어 로직.
        if (pageable.getPageSize() > Post.MAX_PAGE_SIZE) {
            throw new PostException(ErrorCode.POST_INVALID_PAGE_SIZE);
        }

        // Redis 캐시에서 게시글 목록 조회 응답을 먼저 확인
        Optional<PageResponseDto<GetPostsItemResponseDto>> cachedPostList = redisPostService.getPostList(
                currentUserId, status, pageable.getPageNumber(), pageable.getPageSize()
        );

        // 캐시된 응답이 없으면 DB 조회 없이 바로 반환
        if (cachedPostList.isPresent()) {
            return cachedPostList.get();
        }

        // 1. 현재 유저의 학교 ID 조회
        // getUserInfo()는 UserInfoDto를 반환 — universityId 포함
        UserInfoDto currentUser = userService.getUserInfo(currentUserId);
        Long universityId = currentUser.universityId(); //

        // 2. 같은 학교 유저 ID 목록 조회
        List<Long> sameUniversityUserIds = userService.getUserIdsByUniversityId(universityId);

        // 2-1. 다시 만나고 싶지 않아요 관계가 있는 작성자는 목록에서 제외합니다.
        // 관계는 양방향으로 저장되므로, 현재 사용자가 신청자였든 등록자였든 서로의 게시글이 보이지 않습니다.
        List<Long> avoidedUserIds = reviewAvoidanceService.getAvoidedUserIds(currentUserId);
        List<Long> visibleAuthorIds = sameUniversityUserIds.stream()
                .filter(userId -> !avoidedUserIds.contains(userId))
                .toList();

        // 3. 게시글 조회
        Page<Post> postPage;
        if (status == null) {
            // status 없으면 해당 학교 전체 게시글 (상태 무관)
            postPage = postRepository.findByAuthorIdIn(visibleAuthorIds, PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()
            ));
        } else {
            postPage = postRepository.findByAuthorIdInAndStatus(
                    visibleAuthorIds,
                    status,
                    pageable
            );
        }
        // 4. 이번 페이지 게시글들의 작성자 ID만 중복 없이 추출
        //    - postPage.getContent() : 현재 페이지의 실제 List<Post>를 꺼냄
        //    - map(Post::getAuthorId) : 각 게시글에서 작성자 ID만 뽑음
        //    - distinct()             : 한 사람이 글 여러 개 썼을 때 중복 ID 제거 → 불필요한 조회 방지
        //    - toList()               : List<Long>으로 수집
        List<Long> authorIds = postPage.getContent().stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList();

        // 5. 작성자 정보를 IN 쿼리 단 1번으로 한꺼번에 조회
        Map<Long, UserInfoDto> authorMap = userService.getUserInfos(authorIds);

        // 6. 이제 루프 안에서는 DB를 건드리지 않고 Map에서 꺼내 쓰기만 함
        Page<GetPostsItemResponseDto> dtoPage = postPage.map(post -> {
            UserInfoDto authorInfo = authorMap.get(post.getAuthorId());

            // 방어 코드: 혹시 작성자가 빠졌다면(탈퇴/삭제 등) NPE 대신 안전 처리
            if (authorInfo == null) {
                return GetPostsItemResponseDto.from(post, null, null, null, null);
            }
            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber(),
                    authorInfo.mannerTemperature()
            );
        });

//        return PageResponseDto.from(dtoPage);

        // DB 조회 결과를 게시글 목록 API 응답 DTO로 변환
        PageResponseDto<GetPostsItemResponseDto> responseDto = PageResponseDto.from(dtoPage);

        // 동일 요청에서 DB 조회를 줄일 수 있도록 Redis 응답 결과를 저장
        redisPostService.savePostList(
                currentUserId,
                status,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                responseDto
        );

        // 최종 응답 반환
        return responseDto;
    }

    @Override
    public PageResponseDto<GetPostsItemResponseDto> getPostsByAuthor(
            Long authorId,
            Pageable pageable
    ) {
        // 1. 페이지 크기 검증 (최대 50)
        if (pageable.getPageSize() > Post.MAX_PAGE_SIZE) {
            throw new PostException(ErrorCode.POST_INVALID_PAGE_SIZE);
        }

        // 2. 작성자 기준 조회
        Page<Post> postPage = postRepository.findByAuthorId(authorId, pageable);

        Map<Long,UserInfoDto> authorMap = userService.getUserInfos(List.of(authorId));

        // 3. Page<Post> → Page<GetPostsItemResponseDto> 변환
        Page<GetPostsItemResponseDto> dtoPage = postPage.map(post -> {
            UserInfoDto authorInfo = authorMap.get(post.getAuthorId());

            if (authorInfo == null) {
                return GetPostsItemResponseDto.from(post, null, null, null, null);
            }

            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber(),
                    authorInfo.mannerTemperature()
            );
        });

        return PageResponseDto.from(dtoPage);
    }

    @Override
    public Post getPostById(Long postId) {
        // 단순 단건 조회 — findById Optional 반환 → 없으면 POST_001로 변환
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    @Transactional
    public Post getPostByIdWithLock(Long postId) {
        // DB에서 PESSIMISTIC_WRITE 락을 걸고 게시글 조회
        return postRepository.findByIdWithLock(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    public PostInfoDto getPostInfo(Long postId) {
        // 내부적으로 getPostById 재사용 → 중복 제거
        Post post = getPostById(postId);
        return PostInfoDto.from(post);
    }

    @Override
    public GetPostResponseDto getPost(Long postId, Long currentUserId) {

        // 1. 게시글 존재 확인
        Post post = getPostById(postId);

        // 2. 같은 학교 게시글인지 검증
        // 현재 유저와 게시글 작성자의 universityId가 다르면 403
        UserInfoDto currentUser = userService.getUserInfo(currentUserId);
        UserInfoDto author = userService.getUserInfo(post.getAuthorId());

        // 작성자가 탈퇴한 경우에도 게시글은 조회 가능하게 허용
        // (탈퇴 유저 게시글을 완전히 막으면 이미 매칭된 상대방도 못 보는 문제)
        if (author != null && !currentUser.universityId().equals(author.universityId())) {
            throw new PostException(ErrorCode.POST_FORBIDDEN_UNIVERSITY);
        }

        // 3. isMine 결정
        boolean isMine = post.isAuthor(currentUserId);

        // 4. 작성자 정보 (null 방어 — 탈퇴 유저 게시글 처리)
        return GetPostResponseDto.from(
                post,
                author != null ? author.nickname()       : null,
                author != null ? author.major()          : null,
                author != null ? author.studentNumber()  : null,
                isMine
        );
    }

    @Override
    public PostMatchInfoDto getPostMatchInfo(Long postId) {
        Post post = getPostById(postId);
        return PostMatchInfoDto.from(post);
    }

    @Override
    public Map<Long, PostInfoDto> getPostInfos(List<Long> postIds) {

        // 1. 빈 리스트 가드
        //     null / 빈 컬렉션을 IN 절에 넣지 않기 위한 방어
        //     Collections.emptyMap() = 불변 싱글톤 빈 Map (가벼움)
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. findAllById = JpaRepository 기본 제공 IN 쿼리 메서드
        List<Post> posts = postRepository.findAllById(postIds);

        // 3. List<Post> → Map<Long, PostInfoDto> 변환
        return posts.stream()
                .collect(Collectors.toMap(
                        Post::getId,
                        PostInfoDto::from
                ));
    }

    @Override
    public Map<Long, PostMatchInfoDto> getPostMatchInfos(List<Long> postIds) {
        // 1. 빈 리스트 가드 (getPostInfos와 동일)
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 2. soft delete된 게시글 포함 조회
        //    이유: 매칭은 게시글이 삭제되어도 살아있어야 함 (매칭 이력 보존)
        //          findAllById()는 @SQLRestriction으로 삭제된 게시글을 제외하므로
        //          매칭 목록 조회 전용인 findAllByIdIncludingDeleted()를 사용
        List<Post> posts = postRepository.findAllByIdIncludingDeleted(postIds);

        return posts.stream()
                .collect(Collectors.toMap(
                        Post::getId,
                        PostMatchInfoDto::from
                ));
    }

    // 게시글 강제 삭제 사유를 받아서 포인트 반환
    @Override
    public int forceDeletePost(Post post, String reason) {

        // 작성자에게 예치 포인트 전액 환불
        int refundedPoint = post.getAuthorDeposit();
        userPointService.refundPoint(post.getAuthorId(), refundedPoint, null);

        // 소프트 삭제 + 사유 영속화
        // post.delete(reason) 가 deleteReason 세팅 후 deletedAt=now() 세팅
        // @Transactional 더티 체킹으로 트랜잭션 종료 시 두 컬럼 모두 UPDATE
        post.deleteAndReason(reason);

        // 41. 게시글 삭제 알림 - 게시글 작성자에게
        notificationPublisher.sendPostDeleted(
                post.getAuthorId(), // userId (Long)
                post.getId()        // postId (Long) -> 이 부분이 누락되었었습니다!
        );

        return refundedPoint;
    }

    // 작성자 본인이 자신의 삭제된 게시글 사유 조회 (알림 진입로 / 마이페이지용)
    @Override
    public DeletedPostReasonResponseDto getDeletedPostReason(Long postId, Long userId) {

        // 삭제 포함 조회
        Post post = postRepository.findByIdIncludingDeleted(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));

        // 본인 게시글만 사유 열람 가능
        if (!post.isAuthor(userId)) {
            throw new PostException(ErrorCode.POST_NOT_AUTHOR);
        }

        // 실제로 삭제된 글인지 확인
        if (!post.isDeleted()) {
            throw new PostException(ErrorCode.POST_NOT_DELETED);
        }

        return DeletedPostReasonResponseDto.from(post);
    }

    // 강제 삭제 게시글 복구
    @Override
    @Transactional
    public int restorePost(Post post) {

        // 복구할 예치금 -> 삭제 당시 환불했던 책임비
        int redepositPoint = post.getAuthorDeposit();

        // 삭제 때 작성자에게 환불했으므로, 복구 시 다시 차감
        // 잔액 부족 시 user.deduct() 내부에서 예외 발생
        userPointService.deductPoint(post.getAuthorId(), redepositPoint, null);

        // 게시글 복구
        post.restore();

        // 42. 게시글 복구 알림 - 게시글 작성자에게
        notificationPublisher.sendPostRestored(
                post.getAuthorId(), // userId (Long)
                post.getId()        // postId (Long) -> 엔티티 식별자 메서드명에 맞게 입력 (ex: post.getPostId())
        );

        return redepositPoint;
    }

    // 삭제 포함 단건 조회
    @Override
    public Post getPostByIdIncludingDeleted(Long postId) {
        return postRepository.findByIdIncludingDeleted(postId)
                .orElseThrow( () -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    // 관리자 게시글 목록 조회, 전체 대학 조회와 특정 대학 필터 분기 처리
    @Override
    public Page<Post> getPostsForAdmin(List<Long> authorIds, PostStatus status, String keyword, Pageable pageable) {
        if (authorIds == null) {
            return postRepository.findAllForAdmin(status, keyword, pageable);
        }
        return postRepository.findAllForAdminByAuthorIds(authorIds, status, keyword, pageable);
    }

    @Override
    public List<Post> findAiMatchingCandidatePosts(
            List<Long> authorIds,
            Sort sort
    ) {
        Page<Post> posts = postRepository.findByAuthorIdInAndStatusAndMeetAtAfter(
                authorIds,
                PostStatus.OPEN,
                LocalDateTime.now(),
                PageRequest.of(0, 20, sort)
        );

        return posts.getContent();
    }

    // ── 동시성 테스트용 추가 구현 ──────────────────────────────────────

    /**
     * 전략 A 전용: 비관적 락 + NOWAIT
     * Lock(PESSIMISTIC_WRITE) + timeout=0 조합
     *   → JPA가 "SELECT ... FOR UPDATE NOWAIT" 쿼리 실행
     *   → 다른 트랜잭션이 락을 잡고 있으면 즉시 LockTimeoutException
     * Transactional이 필요한 이유:
     *   비관적 락은 반드시 트랜잭션 안에서만 유효
     *   트랜잭션이 없으면 락을 잡자마자 바로 해제되어 의미 없음
     *   MatchConcurrencyService의 @Transactional이 전파(REQUIRED)되므로
     *   별도 선언 없어도 되지만, 명시적으로 선언해서 의도를 드러냄
     */
    @Override
    @Transactional
    public Post getPostWithPessimisticLockNowait(Long postId) {
        // PostRepository에 추가한 findByIdWithPessimisticLockNowait() 호출
        // → @Lock(PESSIMISTIC_WRITE) + @QueryHint(timeout=0) 적용된 메서드
        return postRepository.findByIdWithPessimisticLockNowait(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    /**
     * 전략 B 전용: 비관적 락 + 대기 O
     * Lock(PESSIMISTIC_WRITE) 만 적용 (timeout 힌트 없음)
     *   → JPA가 "SELECT ... FOR UPDATE" 쿼리 실행
     *   → innodb_lock_wait_timeout 설정값만큼 대기 후 타임아웃
     */
    @Override
    @Transactional
    public Post getPostWithPessimisticLock(Long postId) {
        // PostRepository에 추가한 findByIdWithPessimisticLock() 호출
        // → @Lock(PESSIMISTIC_WRITE) 만 적용된 메서드 (NOWAIT 없음)
        return postRepository.findByIdWithPessimisticLock(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    /**
     * 동시성 테스트 전용: 게시글 상태 변경
     * MatchConcurrencyService에서 매칭 확정 시 OPEN → MATCHED 변경에 사용
     * 기존 completePost()는 COMPLETED 전용이라 별도로 분리
     * JPA 변경감지(Dirty Checking) 동작 원리:
     *   Transactional 안에서 조회한 엔티티를 수정하면
     *   트랜잭션 커밋 시점에 JPA가 변경사항을 감지하고 UPDATE 쿼리 자동 실행
     *   → postRepository.save(post) 를 명시적으로 호출하지 않아도 됨
     */
    @Override
    @Transactional
    public void changePostStatus(Long postId, PostStatus status) {
        // 1. 기존 getPostById() 재사용 → 중복 조회 로직 없음
        Post post = getPostById(postId);

        // 2. 도메인 메서드로 상태 변경 (컨벤션: 상태 변경은 엔티티 내부 메서드가 책임)
        //    Post 엔티티에 changeStatus() 도메인 메서드가 있어야 함
        //    없다면 아래처럼 추가: public void changeStatus(PostStatus status) { this.status = status; }
        post.changeStatus(status);

        log.info("[Post] 게시글 상태 변경 - postId: {}, status: {}",
                postId, status);

        // 3. 명시적 save() 없음 → @Transactional + Dirty Checking이 자동 UPDATE 처리
    }

}
