package com.example.team3final.domain.notification.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.service.NotificationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetNotificationsResponseDto>>> getNotifications(@AuthenticationPrincipal UserDetailsImpl userDetails,  // JWT 토큰에서 인증된 유저 정보
                                                                                                         @RequestParam(required = false) Boolean isRead,        // 읽음 여부 필터
                                                                                                         @RequestParam(required = false) NotificationType type, // 알림 유형 필터
                                                                                                         @RequestParam(defaultValue = "0") int page,            // 페이지 번호
                                                                                                         @RequestParam(defaultValue = "20") int size            // 페이지 크기
    ) {
        Long receiverId = userDetails.getUserId();
        int safeSize = Math.min(size, 50);         // 최대 50개로 제한
        Pageable pageable = PageRequest.of(page, safeSize);

        PageResponseDto<GetNotificationsResponseDto> response = notificationService.getNotifications(receiverId, isRead, type, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 전체 읽음 처리 - 알림 목록 진입 시 프론트에서 자동 호출
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponseDto<UpdateAllNotificationsReadResponseDto>> updateAllNotificationsRead(@AuthenticationPrincipal UserDetailsImpl userDetails // JWT 토큰에서 인증된 유저 정보
    ) {
        Long receiverId = userDetails.getUserId();
        UpdateAllNotificationsReadResponseDto response = notificationService.updateAllNotificationsRead(receiverId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponseDto<GetUnreadCountResponseDto>> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails // JWT 토큰에서 인증된 유저 정보
    ) {
        Long receiverId = userDetails.getUserId(); // JWT에서 수신자 ID 추출
        GetUnreadCountResponseDto response = notificationService.getUnreadCount(receiverId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
