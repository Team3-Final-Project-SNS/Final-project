package com.example.team3final.common.exception;

public class ChatException extends ServiceException {

    public ChatException(ErrorCode errorCode) {
        super(errorCode);
    }
}