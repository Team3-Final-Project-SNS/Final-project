package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.post.dto.response.DeletedPostReasonResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.review.service.ReviewAvoidanceService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

// Post 도메인의 조회 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryServiceImpl implements PostQueryService {

    private final PostRepository postRepository;
    private final PostInternalService postInternalService;
    private final UserInternalService userInternalService;
    private final RedisPostService redisPostService;
    private final ReviewAvoidanceService reviewAvoidanceService;
    private final MeetVerificationRepository meetVerificationRepository;

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
                currentUserId, status, pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().toString()
        );

        // 캐시된 응답이 있으면 DB 조회 없이 바로 반환
        if (cachedPostList.isPresent()) {
            return cachedPostList.get();
        }

        // 1. 현재 유저의 학교 ID 조회
        // getUserInfo()는 UserInfoDto를 반환 — universityId 포함
        UserInfoDto currentUser = userInternalService.getUserInfo(currentUserId);
        Long universityId = currentUser.universityId(); //

        // 2. 같은 학교 유저 ID 목록 조회
        List<Long> sameUniversityUserIds = userInternalService.getUserIdsByUniversityId(universityId);

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
        Map<Long, UserInfoDto> authorMap = userInternalService.getUserInfos(authorIds);

        // 6. 이제 루프 안에서는 DB를 건드리지 않고 Map에서 꺼내 쓰기만 함
        Page<GetPostsItemResponseDto> dtoPage = postPage.map(post -> {
            UserInfoDto authorInfo = authorMap.get(post.getAuthorId());
            LocalDateTime meetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByPostId(post.getId())
                    .orElse(post.getMeetAt());

            // 방어 코드: 혹시 작성자가 빠졌다면(탈퇴/삭제 등) NPE 대신 안전 처리
            if (authorInfo == null) {
                return GetPostsItemResponseDto.from(post, null, null, null, null, meetAt);
            }
            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber(),
                    authorInfo.mannerTemperature(),
                    meetAt
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
                pageable.getSort().toString(),
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

        Map<Long,UserInfoDto> authorMap = userInternalService.getUserInfos(List.of(authorId));

        // 3. Page<Post> → Page<GetPostsItemResponseDto> 변환
        Page<GetPostsItemResponseDto> dtoPage = postPage.map(post -> {
            UserInfoDto authorInfo = authorMap.get(post.getAuthorId());
            LocalDateTime meetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByPostId(post.getId())
                    .orElse(post.getMeetAt());

            if (authorInfo == null) {
                return GetPostsItemResponseDto.from(post, null, null, null, null, meetAt);
            }

            return GetPostsItemResponseDto.from(
                    post,
                    authorInfo.nickname(),
                    authorInfo.major(),
                    authorInfo.studentNumber(),
                    authorInfo.mannerTemperature(),
                    meetAt
            );
        });

        return PageResponseDto.from(dtoPage);
    }

    @Override
    public GetPostResponseDto getPost(Long postId, Long currentUserId) {

        // 1. 게시글 존재 확인
        Post post = postInternalService.getPostById(postId);

        // 2. 같은 학교 게시글인지 검증
        // 현재 유저와 게시글 작성자의 universityId가 다르면 403
        UserInfoDto currentUser = userInternalService.getUserInfo(currentUserId);
        UserInfoDto author = userInternalService.getUserInfo(post.getAuthorId());

        // 작성자가 탈퇴한 경우에도 게시글은 조회 가능하게 허용
        // (탈퇴 유저 게시글을 완전히 막으면 이미 매칭된 상대방도 못 보는 문제)
        if (author != null && !currentUser.universityId().equals(author.universityId())) {
            throw new PostException(ErrorCode.POST_FORBIDDEN_UNIVERSITY);
        }

        // 3. isMine 결정
        boolean isMine = post.isAuthor(currentUserId);
        LocalDateTime meetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByPostId(postId)
                .orElse(post.getMeetAt());

        // 4. 작성자 정보 (null 방어 — 탈퇴 유저 게시글 처리)
        return GetPostResponseDto.from(
                post,
                author != null ? author.nickname()       : null,
                author != null ? author.major()          : null,
                author != null ? author.studentNumber()  : null,
                author != null ? author.mannerTemperature() : null,
                isMine,
                meetAt
        );
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
}
