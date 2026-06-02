package com.example.team3final.domain.notification.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.service.NotificationService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 조회 (커서 기반 페이징)
    @GetMapping
    public ResponseEntity<ApiResponseDto<CursorResponseDto<GetNotificationsResponseDto>>> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails, // JWT 토큰에서 인증된 유저 정보
            @RequestParam(required = false) Long cursorId,        // 마지막으로 받은 알림 ID (처음 요청 시 생략 가능 — 생략하면 최신순 첫 페이지 조회)
            @RequestParam(defaultValue = "20") int size           // 페이지 크기 (최대 50)
    ) {
        Long receiverId = userDetails.getUserId();
        CursorResponseDto<GetNotificationsResponseDto> response =
                notificationService.getNotifications(receiverId, cursorId, size);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 전체 읽음 처리 - 알림 목록 진입 시 프론트에서 자동 호출
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponseDto<UpdateAllNotificationsReadResponseDto>> updateAllNotificationsRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails // JWT 토큰에서 인증된 유저 정보
    ) {
        Long receiverId = userDetails.getUserId();
        UpdateAllNotificationsReadResponseDto response =
                notificationService.updateAllNotificationsRead(receiverId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // SSE 연결 - 클라이언트가 실시간 알림 수신을 위해 호출
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal UserDetailsImpl userDetails // JWT 토큰에서 인증된 유저 정보
    ) {
        Long userId = userDetails.getUserId();
        return notificationService.subscribe(userId);
    }

    // 미확인 알림 카운트 조회
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponseDto<GetUnreadCountResponseDto>> getUnreadCount(
            @AuthenticationPrincipal UserDetailsImpl userDetails // JWT 토큰에서 인증된 유저 정보
    ) {
        Long receiverId = userDetails.getUserId(); // JWT에서 수신자 ID 추출
        GetUnreadCountResponseDto response = notificationService.getUnreadCount(receiverId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}