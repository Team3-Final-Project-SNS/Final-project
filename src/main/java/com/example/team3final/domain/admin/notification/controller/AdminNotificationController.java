package com.example.team3final.domain.admin.notification.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateNotificationReadResponseDto;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<CursorResponseDto<GetNotificationsResponseDto>>> getNotifications(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                notificationService.getNotifications(
                        NotificationReceiverType.ADMIN,
                        adminDetails.getAdminId(),
                        cursorId,
                        size
                )
        ));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponseDto<UpdateAllNotificationsReadResponseDto>> readAll(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                notificationService.updateAllNotificationsRead(
                        NotificationReceiverType.ADMIN,
                        adminDetails.getAdminId()
                )
        ));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponseDto<UpdateNotificationReadResponseDto>> read(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                notificationService.updateNotificationRead(
                        NotificationReceiverType.ADMIN,
                        adminDetails.getAdminId(),
                        notificationId
                )
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponseDto<GetUnreadCountResponseDto>> getUnreadCount(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                notificationService.getUnreadCount(
                        NotificationReceiverType.ADMIN,
                        adminDetails.getAdminId()
                )
        ));
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal AdminDetailsImpl adminDetails) {
        return notificationService.subscribe(
                NotificationReceiverType.ADMIN,
                adminDetails.getAdminId()
        );
    }
}
