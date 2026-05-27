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


/**
 * 매칭 AI에서 사용하는 백엔드 Tool입니다.
 *
 * 같은 학교의 모집 중인 식사팟 후보를 조회하고,
 * 로그인 사용자의 신청 가능 여부와 책임비 포인트 충족 여부를 검증합니다.
 *
 * 현재는 MySQL의 사용자, 게시글, 매칭 데이터를 기준으로 후보를 조회하며,
 * 추후 RAG 검색이 추가되면 Retriever가 찾은 postId 후보를
 * 다시 이 Tool에서 검증하는 방식으로 확장할 수 있습니다.
 */
@Component
@RequiredArgsConstructor
public class AiMatchingTool {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MatchRepository matchRepository;


    /**
     * 서비스 내부에서 사용하는 모집글 후보 조회 메서드입니다.
     *
     * 로그인 사용자의 학교 기준으로 같은 학교의 ACTIVE 사용자들이 작성한
     * OPEN 상태의 게시글을 조회하고, 본인 게시글 제외, 이미 신청한 글 여부,
     * 모집 마감 여부, 책임비 포인트 충족 여부를 검증합니다.
     *
     * @param userId 로그인 사용자 ID
     * @param universityId 로그인 사용자의 학교 ID
     * @param userPoint 로그인 사용자의 보유 포인트
     * @param condition 사용자의 자연어 식사 조건
     * @return 매칭 AI 프롬프트에 전달할 모집글 후보 목록
     */
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

    /**
     * LLM Tool Calling에서 사용할 모집글 후보 조회 메서드입니다.
     *
     * 사용자의 이메일과 자연어 조건을 기반으로 신청 가능한 식사팟 후보를 조회합니다.
     * 현재 매칭 서비스에서는 서비스 내부에서 Tool을 먼저 호출해 후보를 프롬프트에 주입하므로,
     * 이 메서드는 추후 LLM 주도 Tool Calling 흐름을 확장할 때 사용할 수 있습니다.
     */
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

    /**
     * 특정 게시글에 대해 사용자가 신청 가능한지 검증하는 Tool입니다.
     *
     * 게시글 상태, 본인 게시글 여부, 이미 신청한 여부,
     * 모집 인원 마감 여부, 책임비 포인트 충족 여부를 확인합니다.
     *
     * 추후 LLM이 특정 게시글을 추천한 뒤 신청 가능 여부를 재검증하는
     * Tool Calling 흐름에서 사용할 수 있습니다.
     */
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

    /**
     * 신청 불가 사유를 사용자에게 안내할 수 있는 문장으로 변환합니다.
     */
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
