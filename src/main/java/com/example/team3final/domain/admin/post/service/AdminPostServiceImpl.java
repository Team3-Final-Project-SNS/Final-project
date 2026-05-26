package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminRepository adminRepository;
    private final PostService postService;

    @Override
    @Transactional
    public AdminDeletePostResponseDto deletePost(Long adminId, Long postId, AdminDeletePostRequestDto requestDto) {

        // 활성화된 SUPER_ADMIN 계정 확인
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // PostService 통해서 강제 삭제 + 환불 처리
        int refundedPoint = postService.forceDeletePost(postId);

        return AdminDeletePostResponseDto.of(postId, requestDto.getReason(), refundedPoint);
    }
}
