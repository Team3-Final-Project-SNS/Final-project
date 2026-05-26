package com.example.team3final.common.exception;

public class AiException extends ServiceException {
    public AiException(ErrorCode errorCode) {
        super(errorCode);
    }
}
