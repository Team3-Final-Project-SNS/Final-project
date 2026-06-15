package com.example.team3final.domain.notification.exception;

// 알림 이벤트의 필수값 또는 타입별 정책이 올바르지 않을 때 발생하는 예외
public class InvalidNotificationEventException extends RuntimeException {

    public InvalidNotificationEventException(String message) {
        super(message);
    }

    public InvalidNotificationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
