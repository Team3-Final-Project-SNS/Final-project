package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostsResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminRepository adminRepository;
    private final PostService postService;
    private final ReportService reportService;
    private final UserService userService;

    @Override
    @Transactional
    public AdminDeletePostResponseDto deletePost(Long adminId, Long postId, AdminDeletePostRequestDto requestDto) {
        // 환불 + 삭제를 하나의 트랜잭션으로 묶음

        // 관리자 검증
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 슈퍼 어드민 확인
        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // 게시글 조회
        Post post = postService.getPostById(postId);

        // OPEN 상태인지 확인
        if (!post.isOpen()) {
            throw new AdminException(ErrorCode.ADMIN_POST_NOT_OPEN);
        }

        // 신고 연계 검증
        // reportId로 신고 조회
        Report report = reportService.getReportById(requestDto.getReportId());

        // 신고의 targetId가 요청 postId와 일치하는지 확인
        if (!report.getTargetId().equals(postId)) {
            throw new AdminException(ErrorCode.ADMIN_POST_ID_MISMATCH);
        }

        // 신고가 ACCEPTE 상태인지 확인
        if (report.getStatus() != ReportStatus.ACCEPTED) {
            throw new AdminException(ErrorCode.ADMIN_NOT_ACCEPTED);
        }

        // PostService 통해서 강제 삭제 + 환불 처리
        int refundedPoint = postService.forceDeletePost(post);

        return AdminDeletePostResponseDto.of(
                postId,
                requestDto.getReportId(),
                requestDto.getReason(),
                refundedPoint,
                LocalDateTime.now()
        );
    }

    // 관리자 게시물 목록 조회
    @Override
    public PageResponseDto<AdminGetPostsResponseDto> getPosts(
            Long adminId,
            Long universityId,
            String authorNickname,
            PostStatus status,
            String keyword,
            Pageable pageable) {

        // 관리자 검증
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // universityIds -> authorIds 변환
        // universityId 없으면 null 그대로 넘김 → PostService에서 전체 조회 처리
        // universityId 있으면 해당 대학 유저 ID 목록 조회
        List<Long> authorIds = null;
        if (universityId != null) {
            authorIds = userService.getUserIdsByUniversityId(universityId);

            // 해당 대학에 유저가 한 명도 없으면 빈 페이지 즉시 반환
            // authorIds가 빈 리스트인 채로 IN 절에 들어가면 쿼리 오류 또는 전체 조회가 될 수 있음
            if (authorIds.isEmpty()) {
                return PageResponseDto.from(Page.empty(pageable));
            }
        }

        // authorNickname → authorIds 변환 + 교집합 처리
        // universityId 없음 + authorNickname 있음 → 닉네임 검색 결과 그대로 사용
        // universityId 있음 + authorNickname 있음 -> 대학 필터 authorIds와 닉네임 검색 authorIds의 교집합
        if (authorNickname != null) {
            List<Long> nicknameAuthorIds = userService.getUserIdsByNickname(authorNickname);

            // 닉네임 검색 결과가 없으면 즉시 빈 페이지 반환
            if (nicknameAuthorIds.isEmpty()) {
                return PageResponseDto.from(Page.empty(pageable));
            }

            if (authorIds != null) {
                // universityId 필터가 있으면 교집합 처리
                authorIds.retainAll(nicknameAuthorIds);
                // retainAll(): List A에서 List B에 없는 요소를 전부 제거 → 교집합만 남음
                if (authorIds.isEmpty()) {
                    return PageResponseDto.from(Page.empty(pageable));
                }
            } else {
                // universityId 필터 없으면 닉네임 검색 결과 그대로 사용
                authorIds = nicknameAuthorIds;
            }
        }

        // Post 목록 조회
        Page<Post> posts = postService.getPostsForAdmin(authorIds, status, keyword, pageable);

        // N+1 방지, authorId 목록 한 번에 추출 후 닉네임 bulk 조회
        List<Long> postAuthorIds = posts.getContent()
                .stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList();

        Map<Long, String> nicknameMap = userService.getUserNicknameMap(postAuthorIds);

        // 5. Post 엔티티 → 응답 DTO 변환
        Page<AdminGetPostsResponseDto> dtoPage = posts.map(post ->
                AdminGetPostsResponseDto.of(
                        post,
                        nicknameMap.getOrDefault(post.getAuthorId(), "알 수 없음")
                )
        );

        return PageResponseDto.from(dtoPage);
    }

    @Override
    public AdminGetPostResponseDto getPost(Long adminId, Long postId) {

        // 관리자 검증
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // postId 조회
        Post post = postService.getPostById(postId);

        // 작성자 닉네임 단건 조회
        String authorNickname = userService.getUserInfo(post.getAuthorId()).nickname();

        // 4. DTO 변환 후 반환
        return AdminGetPostResponseDto.of(post, authorNickname);
    }
}
