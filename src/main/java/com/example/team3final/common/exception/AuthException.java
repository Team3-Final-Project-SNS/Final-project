package com.example.team3final.common.exception;

public class AuthException extends ServiceException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
