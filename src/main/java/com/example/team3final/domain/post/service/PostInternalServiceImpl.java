package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Post 도메인의 타 도메인 호출용 내부 조회 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostInternalServiceImpl implements PostInternalService {

    private final PostRepository postRepository;

    @Override
    public Post getPostById(Long postId) {
        // 단순 단건 조회 — findById Optional 반환 → 없으면 POST_001로 변환
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    // 삭제 포함 단건 조회
    @Override
    public Post getPostByIdIncludingDeleted(Long postId) {
        return postRepository.findByIdIncludingDeleted(postId)
                .orElseThrow( () -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    public PostInfoDto getPostInfo(Long postId) {
        // 내부적으로 getPostById 재사용 → 중복 제거
        Post post = getPostById(postId);
        return PostInfoDto.from(post);
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
    @Transactional
    public Post getPostByIdWithLock(Long postId) {
        // DB에서 PESSIMISTIC_WRITE 락을 걸고 게시글 조회
        return postRepository.findByIdWithLock(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    @Transactional
    public Post getPostWithPessimisticLockNowait(Long postId) {
        // PostRepository에 추가한 findByIdWithPessimisticLockNowait() 호출
        // → @Lock(PESSIMISTIC_WRITE) + @QueryHint(timeout=0) 적용된 메서드
        return postRepository.findByIdWithPessimisticLockNowait(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    @Transactional
    public Post getPostWithPessimisticLock(Long postId) {
        // PostRepository에 추가한 findByIdWithPessimisticLock() 호출
        // → @Lock(PESSIMISTIC_WRITE) 만 적용된 메서드 (NOWAIT 없음)
        return postRepository.findByIdWithPessimisticLock(postId)
                .orElseThrow(() -> new PostException(ErrorCode.POST_NOT_FOUND));
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

    @Override
    public List<Post> findAiMatchingCandidatePostsByIds(
            List<Long> postIds,
            List<Long> authorIds
    ) {
        if (postIds == null || postIds.isEmpty() || authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> rankByPostId = new java.util.HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            rankByPostId.put(postIds.get(i), i);
        }

        // 벡터 검색 결과의 유사도 순서를 유지하면서, MySQL의 최신 게시글 상태로 한 번 더 걸러냅니다.
        return postRepository.findByIdInAndAuthorIdInAndStatusAndMeetAtAfter(
                        postIds,
                        authorIds,
                        PostStatus.OPEN,
                        LocalDateTime.now()
                )
                .stream()
                .sorted(java.util.Comparator.comparingInt(post ->
                        rankByPostId.getOrDefault(post.getId(), Integer.MAX_VALUE)))
                .toList();
    }
}
