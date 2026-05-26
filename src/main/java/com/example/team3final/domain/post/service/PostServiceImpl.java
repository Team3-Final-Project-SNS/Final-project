package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
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
                .content(request.getContent()) // null이면 null로 저장 (선택 필드)
                .authorDeposit(request.getAuthorDeposit())
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
                 userPointService.deductPoint(userId, diff, null);
            } else if (diff < 0) {
                // 감액: |diff|만큼 환불
                 userPointService.refundPoint(userId, Math.abs(diff), null);
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
        // TODO: User 도메인 머지 후 실제 호출로 교체
        // Long universityId = userService.getUserInfo(currentUserId)...; // 학교 조회 메서드 필요
        Long universityId = 1L; // 임시값

        // 2. 같은 학교 유저 ID 목록 조회
        // TODO: User 도메인 머지 후 실제 호출로 교체
        List<Long> sameUniversityUserIds = null; // null로 두고 아래에서 분기

        // 3. 게시글 조회
        Page<Post> postPage;
        if (sameUniversityUserIds == null) {
            // 임시 분기: User 도메인 머지 전까지 학교 필터 없이 전체 조회
            postPage = postRepository.findAll(
                    PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            pageable.getSort()
                    )
            );
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
            // TODO: 탈퇴 유저 표기 정책이 정해지면 그에 맞게 보완 (예: "(알 수 없음)")
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
        // TODO: User 도메인 머지 후 활성화

        // 3. isMine 결정 — 조회자가 작성자 본인이면 true
        boolean isMine = post.isAuthor(currentUserId);

        // 4. 작성자 정보 조회
        // getUserInfo 한 번으로 nickname/major/studentNumber 모두 확보 (호출 1회로 N+1 방지)
        UserInfoDto authorInfo = userService.getUserInfo(post.getAuthorId());

        // 5. DTO 조립
        return GetPostResponseDto.from(
                post,
                authorInfo.nickname(),
                authorInfo.major(),
                authorInfo.studentNumber(),
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
        // 2. postId IN (...) 단일 쿼리로 게시글 일괄 조회
        List<Post> posts = postRepository.findAllById(postIds);

        return posts.stream()
                .collect(Collectors.toMap(
                        Post::getId,
                        PostMatchInfoDto::from
                ));
    }

    @Override
    public int forceDeletePost(Long postId) {

        // 게시글 조회
        Post post = getPostById(postId);

        // OPEN 상태 확인
        if (!post.isOpen()){
            throw new PostException(ErrorCode.POST_NOT_OPEN);
        }

        // 작성자에게 예치 포인트 전액 환불
        int refundedPoint = post.getAuthorDeposit();
        userPointService.refundPoint(post.getAuthorId(), refundedPoint, null);

        // 게시글 삭제
        postRepository.delete(post);

        return refundedPoint;
    }
}
