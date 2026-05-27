package com.example.team3final.domain.notification.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.service.NotificationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import kotlin.RequiresOptIn;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetNotificationsResponseDto>>> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails, // JWT 토큰에서 인증된 유저 정보
            @RequestParam(required = false) Boolean isRead,       // 읽음 여부 필터
            @RequestParam(required = false) NotificationType type, // 알림 유형 필터
            @RequestParam(defaultValue = "0") int page,           // 페이지 번호
            @RequestParam(defaultValue = "20") int size           // 페이지 크기
    ) {
        Long receiverId = userDetails.getUserId();
        int safeSize = Math.min(size, 50);         // 최대 50개로 제한
        Pageable pageable = PageRequest.of(page, safeSize);

        PageResponseDto<GetNotificationsResponseDto> response =
                notificationService.getNotifications(receiverId, isRead, type, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
