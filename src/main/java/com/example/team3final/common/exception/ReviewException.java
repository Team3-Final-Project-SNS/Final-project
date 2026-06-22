package com.example.team3final.common.exception;

public class ReviewException extends ServiceException {
    public ReviewException(ErrorCode errorCode) {
        super(errorCode);
    }
}
