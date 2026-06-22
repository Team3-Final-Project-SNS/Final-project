package com.example.team3final.common.exception;

public class PostException extends ServiceException {
    public PostException(ErrorCode errorCode) {
        super(errorCode);
    }
}
