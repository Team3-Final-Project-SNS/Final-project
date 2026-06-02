package com.example.team3final.common.exception;

public class NotificationException extends ServiceException {

    public NotificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}