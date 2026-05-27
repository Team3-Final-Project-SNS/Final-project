package com.example.team3final.domain.ai.matching.tool;


import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.UserRepository;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiMatchingTool {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MatchRepository matchRepository;

    public List<AiMatchingPostToolResult> searchRecruitingMealPosts(
            Long userId,
            Long universityId,
            int userPoint,
            String condition
    ) {
        List<Long> sameUniversityUserIds =
                userRepository.findActiveUserIdsByUniversityId(universityId);

        if (sameUniversityUserIds.isEmpty()) {
            return List.of();
        }

        Page<Post> posts = postRepository.findByAuthorIdInAndStatus(
                sameUniversityUserIds,
                PostStatus.OPEN,
                PageRequest.of(0, 5)
        );

        return posts.stream()
                .filter(post -> !post.isAuthor(userId))
                .map(post -> {
                    boolean alreadyApplied =
                            matchRepository.existsByPostIdAndApplicantId(post.getId(), userId);

                    boolean pointAffordable = userPoint >= post.getAuthorDeposit();

                    boolean applicationAvailable =
                            post.isOpen()
                                    && !post.isFull()
                                    && !alreadyApplied
                                    && pointAffordable;

                    String unavailableReason = resolveUnavailableReason(
                            post,
                            alreadyApplied,
                            pointAffordable
                    );

                    return new AiMatchingPostToolResult(
                            post.getId(),
                            post.getPlaceName(),
                            post.getMeetAt().toString(),
                            post.getAuthorDeposit(),
                            post.getContent(),
                            applicationAvailable,
                            pointAffordable,
                            unavailableReason
                    );
                })
                .toList();
    }

    @Tool(
            description = "사용자의 자연어 조건에 맞는 모집 중인 식사팟을 조회합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public List<AiMatchingPostToolResult> searchRecruitingMealPostsForAi(
            @ToolParam(description = "사용자의 이메일", required = true)
            String email,

            @ToolParam(description = "사용자의 식사 조건. 예: 오늘 저녁 조용하게 밥 먹을 사람", required = true)
            String condition
    ) {
        User user = userService.findByEmail(email);

        return searchRecruitingMealPosts(
                user.getId(),
                user.getUniversityId(),
                user.getPoint(),
                condition
        );
    }

    @Tool(
            description = "특정 게시글에 사용자가 신청 가능한지 확인합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public AiMatchingPostToolResult checkApplicationAvailability(
            @ToolParam(description = "사용자의 이메일", required = true)
            String email,

            @ToolParam(description = "게시글 ID", required = true)
            Long postId
    ) {
        User user = userService.findByEmail(email);

        Post post = postRepository.findById(postId)
                .orElseThrow();

        boolean alreadyApplied =
                matchRepository.existsByPostIdAndApplicantId(post.getId(), user.getId());

        boolean pointAffordable = user.getPoint() >= post.getAuthorDeposit();

        boolean applicationAvailable =
                post.isOpen()
                        && !post.isFull()
                        && !post.isAuthor(user.getId())
                        && !alreadyApplied
                        && pointAffordable;

        String unavailableReason = resolveUnavailableReason(
                post,
                alreadyApplied,
                pointAffordable
        );

        if (post.isAuthor(user.getId())) {
            unavailableReason = "본인 게시글에는 신청할 수 없습니다.";
        }

        return new AiMatchingPostToolResult(
                post.getId(),
                post.getPlaceName(),
                post.getMeetAt().toString(),
                post.getAuthorDeposit(),
                post.getContent(),
                applicationAvailable,
                pointAffordable,
                unavailableReason
        );
    }

    private String resolveUnavailableReason(
            Post post,
            boolean alreadyApplied,
            boolean pointAffordable
    ) {
        if (!post.isOpen()) {
            return "모집 중인 게시글이 아닙니다.";
        }

        if (post.isFull()) {
            return "모집 인원이 마감되었습니다.";
        }

        if (alreadyApplied) {
            return "이미 신청한 게시글입니다.";
        }

        if (!pointAffordable) {
            return "보유 포인트가 책임비보다 부족합니다.";
        }

        return null;
    }
}
