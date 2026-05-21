package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostQueryServiceImpl implements PostQueryService {

    private final PostRepository postRepository;

    // ⚠️ User 도메인 머지 후 활성화 — 시그니처만 합의된 상태
    // private final UserQueryService userQueryService;

    @Override
    public PageResponseDto<GetPostsItemResponseDto> getPosts(
            Long currentUserId,
            PostStatus status,
            Pageable pageable
    ) {
        // 1. 현재 유저의 학교 ID 조회
        // TODO: User 도메인 머지 후 실제 호출로 교체
        // Long universityId = userQueryService.getUniversityIdByUserId(currentUserId);
        Long universityId = 1L; // 임시값 — 같은 학교 모든 유저가 universityId=1 이라고 가정

        // 2. 같은 학교 유저 ID 목록 조회
        // TODO: User 도메인 머지 후 실제 호출로 교체
        // List<Long> sameUniversityUserIds = userQueryService.getUserIdsByUniversityId(universityId);
        //
        List<Long> sameUniversityUserIds = null; // null로 두고 아래에서 분기 처리

        // 3. 게시글 조회
        Page<Post> postPage;
        if (sameUniversityUserIds == null) {
            // 임시 분기: User 도메인 머지 전까지는 학교 필터 없이 전체 조회
            // Pageable에 정렬이 이미 포함돼 있으므로 그대로 전달
            postPage = postRepository.findAll(
                    PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            pageable.getSort()
                    )
            );
            // status 필터는 임시 fallback이라 일단 무시 (User 도메인 머지 후 정상화)
        } else {
            postPage = postRepository.findByAuthorIdInAndStatus(
                    sameUniversityUserIds,
                    status,
                    pageable
            );
        }

        // 4. Page<Post> -> Page<GetPostsItemResponse> 변환
        // Page.map(): 컨텐츠만 변환하고 페이징 메타데이터(totalElements 등)는 보존
        Page<GetPostsItemResponseDto> dtoPage = postPage.map(post -> {
            // TODO: User 도메인 머지 후 실제 조회로 교체
            // User author = userQueryService.getUserById(post.getAuthorId());
            // return GetPostsItemResponse.from(post, author.getNickname(), author.getMajor(), author.getStudentNumber());

            // 임시값 — 명세서 응답 형식만 맞춰둠
            return GetPostsItemResponseDto.from(
                    post,
                    "임시닉네임",
                    "임시학과",
                    "00"
            );
        });

        // 5. 공통 PageResponseDto로 래핑
        // 이미 만들어진 공통 응답 포맷 재사용 → content/page/size/totalElements/totalPages/hasNext
        return PageResponseDto.from(dtoPage);
    }

    @Override
    public Post getPostById(Long postId) {
        // 단순 단건 조회 — Match/GPS 도메인이 호출
        // findById가 Optional 반환 → 없으면 POST_001로 변환
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    public PostInfoDto getPostInfo(Long postId) {
        // 내부적으로는 getPostById 재사용 → 코드 중복 제거
        // 엔티티를 조회한 후 DTO로 변환 (필요한 필드만 추림)
        Post post = getPostById(postId);
        return PostInfoDto.from(post);
    }

    @Override
    public GetPostResponseDto getPost(Long postId, Long currentUserId) {

        // 1. 게시글 존재 확인
        Post post = getPostById(postId);

        // 2.같은 학교 게시글인지 검증
        // TODO: User 도메인 머지 후 활성화
        // Long viewerUniversityId = userQueryService.getUniversityIdByUserId(currentUserId);
        // Long authorUniversityId = userQueryService.getUniversityIdByUserId(post.getAuthorId());
        // if (!viewerUniversityId.equals(authorUniversityId)) {
        //     throw new PostException(ErrorCode.POST_FORBIDDEN_UNIVERSITY);
        // }

        // 3. isMine 결정
        // 조회자가 작성자 본인이면 true, 아니면 false

        boolean isMine = post.isAuthor(currentUserId);

        // 4. 작성자 정보 조회
        // TODO: User 도메인 머지 후 실제 조회로 교체
        // User author = userQueryService.getUserById(post.getAuthorId());
        // String authorNickname = author.getNickname();
        // String authorMajor = author.getMajor();
        // String authorStudentNumber = author.getStudentNumber();

        // ※ 현재는 임시값 — 명세서 응답 형식만 맞춰둠 (getPosts와 동일 패턴)
        String authorNickname = "임시닉네임";
        String authorMajor = "임시학과";
        String authorStudentNumber = "00";

        // ===== 5단계: DTO 조립 =====
        // 엔티티(Post) + User 정보 + 컨텍스트(isMine)을 한 곳에서 모아 DTO로 변환
        return GetPostResponseDto.from(
                post,
                authorNickname,
                authorMajor,
                authorStudentNumber,
                isMine
        );
    }

    @Override
    public PostMatchInfoDto getPostMatchInfo(Long postId) {
        Post post = getPostById(postId);
        return PostMatchInfoDto.from(post);
    }
}
