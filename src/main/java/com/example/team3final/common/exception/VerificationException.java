package com.example.team3final.common.exception;

public class VerificationException extends ServiceException {

    public VerificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
