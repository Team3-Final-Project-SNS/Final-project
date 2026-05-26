package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminRepository adminRepository;
    private final PostService postService;
    private final ReportService reportService;

    @Override
    @Transactional
    public AdminDeletePostResponseDto deletePost(Long adminId, Long postId, AdminDeletePostRequestDto requestDto) {
        // 환불 + 삭제를 하나의 트랜잭션으로 묶음

        // 관리자 검증
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

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
            throw new AdminException(ErrorCode.REPORT_POST_ID_MISMATCH);
        }

        // 신고가 ACCEPTE 상태인지 확인
        if (report.getStatus() != ReportStatus.ACCEPTED) {
            throw new AdminException(ErrorCode.REPORT_NOT_ACCEPTED);
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
}
