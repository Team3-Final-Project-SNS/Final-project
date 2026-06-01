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
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostServiceImpl implements PostService{

    private final PostRepository postRepository;
    private final UserService userService;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;


    @Override
    @Transactional
    public CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request) {

        // 1. 비즈니스 규칙 검증
        if (request.getMeetAt().isBefore(LocalDateTime.now())) {
            throw new PostException(ErrorCode.POST_INVALID_MEET_AT);
        }

        // 책임비 검증 - (1) 최소 200P 단위, (2) 100P 단위
        if (request.getAuthorDeposit() < Post.MIN_AUTHOR_DEPOSIT
                || request.getAuthorDeposit() % Post.DEPOSIT_UNIT != 0) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
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
            // 100P 단위 검증
            if (newDeposit % Post.DEPOSIT_UNIT != 0) {
                throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
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

        // 2. 도메인 메서드 호출 — 상태 전이 규칙은 엔티티가 책임
        post.complete();
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

        // 1. 현재 유저의 학교 ID 조회
        // getUserInfo()는 UserInfoDto를 반환 — universityId 포함
        UserInfoDto currentUser = userService.getUserInfo(currentUserId);
        Long universityId = currentUser.universityId(); //

        // 2. 같은 학교 유저 ID 목록 조회
        List<Long> sameUniversityUserIds = userService.getUserIdsByUniversityId(universityId);

        // 3. 게시글 조회
        Page<Post> postPage;
        if (status == null) {
            // status 없으면 해당 학교 전체 게시글 (상태 무관)
            postPage = postRepository.findByAuthorIdIn(sameUniversityUserIds, PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()
            ));
        } else {
            postPage = postRepository.findByAuthorIdInAndStatus(
                    sameUniversityUserIds,
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
                return GetPostsItemResponseDto.from(post, null, null, null);
            }
            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber()
            );
        });

        return PageResponseDto.from(dtoPage);
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
                return GetPostsItemResponseDto.from(post, null, null, null);
            }

            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber()
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

    @Override
    public int forceDeletePost(Post post) {

        // 작성자에게 예치 포인트 전액 환불
        int refundedPoint = post.getAuthorDeposit();

        userPointService.refundPoint(post.getAuthorId(), refundedPoint, null);

        // 게시글 소프트 삭제
        //    postRepository.delete(post) 대신 post.delete() 호출
        //
        //    이유: postRepository.delete()는 JPA가 내부적으로 @SQLDelete 어노테이션의
        //    "UPDATE posts SET deleted_at = NOW() WHERE post_id = ?"를 실행하긴 하지만,
        //    코드만 보면 "hard delete처럼 보임" → 가독성 저하 + 실수 위험
        //
        //    post.delete()를 명시적으로 호출하면:
        //    - SoftDeleteEntity.delete()가 deletedAt = LocalDateTime.now() 세팅
        //    - @Transactional + 더티 체킹으로 트랜잭션 종료 시 자동 UPDATE 쿼리 실행
        //    - 일반 유저 deletePost()와 동일한 방식 → 코드 일관성 유지
        post.delete();

        // 22번 알림 - 게시글 작성자에게 강제 삭제 안내 발송
        notificationPublisher.sendSystem(
                post.getAuthorId(),
                "게시글이 삭제되었습니다.",
                "해당 게시물이 신고 접수 및 관리자 판단에 의해 삭제되었습니다. 자세한 사항은 고객센터를 확인해 주세요."
        );

        return refundedPoint;
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

}
